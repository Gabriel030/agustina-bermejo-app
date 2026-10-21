package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Runs real threads against Postgres in eleven scenarios, each one seeding invoices and waiting for the asynchronous settlement events to drain")
class LateFeeSettlementConcurrencyIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private LateFeeOnInvoiceSettledListener settledListener;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private LateFeeChargeRepository chargeRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private void runInParallel(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Future<Object> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-332")
    void paymentDatedTheLastDayAndMonthCloseRunningTogetherIssueASingleDebitNote() throws Exception {
        arrange(2231);
        publishDefaults(2231);
        Invoice origin = overdueInstallmentDueOn(d(2231, 3, 11));
        receiveMoney(origin, "100000.00", d(2231, 3, 31));

        runInParallel(List.of(() -> {
            allocateOn(origin, d(2231, 4, 1));
            return null;
        }, () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2231, 4, 1))));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2231-03-12..2231-03-31:20:4000.00");
        assertScenarioInvariants(d(2231, 4, 1));
    }

    @Test
    @Tag("D-332")
    void paymentDatedTheFirstOfTheMonthAndMonthCloseRunningTogetherIssueOneDebitNotePerMonth() throws Exception {
        arrange(2232);
        publishDefaults(2232);
        Invoice origin = overdueInstallmentDueOn(d(2232, 3, 11));
        receiveMoney(origin, "100000.00", d(2232, 4, 1));

        runInParallel(List.of(() -> {
            allocateOn(origin, d(2232, 4, 1));
            return null;
        }, () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2232, 4, 1))));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2232-03-12..2232-03-31:20:4000.00", "2232-04-01..2232-04-01:1:200.00");
        assertScenarioInvariants(d(2232, 4, 1));
    }

    @Test
    @Tag("D-332")
    void thePaymentEventDeliveredTwiceAndTheNightlyRunIssueNoDuplicates() {
        arrange(2233);
        publishDefaults(2233);
        Invoice origin = overdueInstallmentDueOn(d(2233, 3, 11));
        payInFull(origin, d(2233, 3, 20), d(2233, 3, 20));
        awaitEventsDrained();
        InvoicePaidDomainEvent again = InvoicePaidDomainEvent.paidFor(origin.getId(), origin.getStudentCurrentAccountId(),
                InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), d(2233, 3, 20));

        settledListener.reconcileLateFeesOfSettledInvoice(again);
        settledListener.reconcileLateFeesOfSettledInvoice(again);
        accrualStep.accrueLateFees(d(2233, 3, 21));

        assertThat(summaryOf(origin)).containsExactly("2233-03-12..2233-03-20:9:1800.00");
        assertScenarioInvariants(d(2233, 3, 21));
    }

    @Test
    @Tag("D-332")
    @Tag("D-290")
    void reversalAndMonthCloseRunningTogetherKeepTheCoverageExact() throws Exception {
        arrange(2234);
        publishDefaults(2234);
        Invoice origin = overdueInstallmentDueOn(d(2234, 3, 11));
        reconcile(origin, d(2234, 4, 1));
        CurrentAccountEntry credit = payInFull(origin, d(2234, 4, 9), d(2234, 4, 9));
        awaitEventsDrained();

        runInParallel(List.of(() -> {
            reversePayment(origin, credit, d(2234, 5, 1), 0);
            return null;
        }, () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2234, 5, 1))));
        awaitEventsDrained();
        reconcile(origin, d(2234, 5, 1));

        assertThat(summaryOf(origin)).containsExactly("2234-03-12..2234-03-31:20:4000.00", "2234-04-01..2234-04-09:9:1800.00", "2234-04-10..2234-04-30:21:4200.00");
        assertScenarioInvariants(d(2234, 5, 1));
    }

    @Test
    @Tag("D-332")
    void aPaymentWhoseListenerNeverRanIsRecoveredOnceByTheNightlyRun() {
        arrange(2235);
        publishDefaults(2235);
        Invoice origin = overdueInstallmentDueOn(d(2235, 3, 11));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2235, 3, 20), d(2235, 3, 20));
        assertThat(summaryOf(origin)).isEmpty();

        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2235, 3, 25));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2235, 3, 26));

        assertThat(first.settlementDebitNotesRecovered()).isEqualTo(1);
        assertThat(second.settlementDebitNotesRecovered()).isZero();
        assertThat(summaryOf(origin)).containsExactly("2235-03-12..2235-03-20:9:1800.00");
        assertThat(chargeRepository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2235, 2, 1), 10)).doesNotContain(origin.getId());
        assertScenarioInvariants(d(2235, 3, 26));
    }

    @Test
    @Tag("D-378")
    @Tag("D-243")
    void aSettlementWhoseListenerNeverRanIsAnnulledOnceByTheNightlyRun() {
        arrange(2236);
        publishDefaults(2236);
        Invoice origin = overdueInstallmentDueOn(d(2236, 3, 11));
        reconcile(origin, d(2236, 4, 2));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2236, 3, 28), d(2236, 4, 2));
        double before = meterRegistry.counter("billing_late_fees_overcharged_days_total").count();

        MutableClock.INSTANCE.setDate(d(2236, 4, 3));
        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2236, 4, 3));
        MutableClock.INSTANCE.setDate(d(2236, 4, 4));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2236, 4, 4));

        assertThat(first.settlementCandidates()).isEqualTo(1);
        assertThat(second.settlementCandidates()).isZero();
        assertThat(meterRegistry.counter("billing_late_fees_overcharged_days_total").count() - before).isEqualTo(3.0);
        assertThat(summaryOf(origin)).containsExactly("2236-03-12..2236-03-31:20:4000.00");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ? and reason = 'LATE_FEE_ANNULMENT'", Long.class, debitNoteId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select sum(total_amount) from credit_note where invoice_id = ?", java.math.BigDecimal.class, debitNoteId)).isEqualByComparingTo("600.00");
        assertScenarioInvariants(d(2236, 4, 4));
    }

    @Test
    @Tag("D-331")
    void aPaymentInsideTheGraceWithoutChargedDaysIsNotACandidateOfTheNightlyRecovery() {
        arrange(2237);
        publish(10, "0.002", 10, true, true, d(2237, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2237, 3, 11));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2237, 3, 20), d(2237, 3, 20));

        assertThat(chargeRepository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2237, 3, 1), 50)).doesNotContain(origin.getId());
        assertScenarioInvariants(d(2237, 3, 20));
    }

    @Test
    @Tag("D-331")
    @Tag("D-378")
    void aPaymentInsideTheGraceWithChargedDaysIsACandidateOfTheNightlyRecoveryAndLeavesNoNetDay() {
        arrange(2260);
        publish(10, "0.002", 10, true, true, d(2260, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2260, 3, 11));
        reconcile(origin, d(2260, 4, 2));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2260, 3, 20), d(2260, 4, 2));

        assertThat(chargeRepository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2260, 3, 1), 50)).contains(origin.getId());
        MutableClock.INSTANCE.setDate(d(2260, 4, 3));
        accrualStep.accrueLateFees(d(2260, 4, 3));
        MutableClock.INSTANCE.setDate(d(2260, 4, 4));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2260, 4, 4));

        assertThat(second.settlementCandidates()).isZero();
        assertThat(chargeRepository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2260, 3, 1), 50)).doesNotContain(origin.getId());
        assertThat(jdbc.queryForMap("select cause, corrected_days, payment_date::text as payment_date from late_fee_charge_correction where late_fee_charge_id = ?",
                chargesOf(origin).get(0).get("id"))).containsEntry("cause", "PAYMENT_WITHIN_GRACE").containsEntry("corrected_days", 20).containsEntry("payment_date", "2260-03-20");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, chargesOf(origin).get(0).get("debit_note_id"))).isEqualTo("CANCELLED");
        assertScenarioInvariants(d(2260, 4, 4));
    }

    @Test
    @Tag("D-325")
    void aLateRedeliveredPaymentEventIssuesWithTheRealDayButKeepsTheFactDate() {
        arrange(2238);
        publishDefaults(2238);
        Invoice origin = overdueInstallmentDueOn(d(2238, 3, 11));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2238, 3, 20), d(2238, 3, 20));
        MutableClock.INSTANCE.setDate(d(2238, 5, 10));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(origin.getId(),
                origin.getStudentCurrentAccountId(), InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), d(2238, 3, 20))));
        awaitEventsDrained();

        assertThat(chargesOf(origin).get(0).get("fact_date").toString()).isEqualTo("2238-03-20");
        assertThat(chargesOf(origin).get(0).get("nd_issue").toString()).isEqualTo("2238-05-10");
        awaitSecretariatNotice(1L);
        assertScenarioInvariants(d(2238, 5, 10));
    }

    @Test
    @Tag("D-332")
    void theCompletenessInvariantDetectsAPaidInstallmentWithoutItsFinalDebitNoteAndPassesOnceRecovered() {
        arrange(2239);
        publishDefaults(2239);
        Invoice origin = overdueInstallmentDueOn(d(2239, 3, 11));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2239, 3, 20), d(2239, 3, 20));

        assertThatThrownBy(() -> invariants.assertLateFeesAreCompleteAt(d(2239, 3, 21))).hasMessageContaining("I-11h").hasMessageContaining("2239-03-12");
        accrualStep.accrueLateFees(d(2239, 3, 21));

        invariants.assertLateFeesAreCompleteAt(d(2239, 3, 21));
    }

    private void awaitSecretariatNotice(long expected) {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).until(() -> jdbc.queryForObject(
                "select count(*) from processed_effect where handler_name = 'LateFeeNotificationListener.notifySecretariatWhenIssuedAfterFiscalWindow'", Long.class) >= expected);
    }

    private void publishChargingDisabledFromTheFifthOfApril(int year) {
        publishDefaults(year);
        publish(0, "0.002", 10, true, false, d(year, 4, 5));
    }

    @Test
    @Tag("D-294")
    @Tag("D-332")
    void aChargingChangeInsideThePaymentMonthIsAppliedWhenTheListenerRunsOnTime() {
        arrange(2240);
        publishChargingDisabledFromTheFifthOfApril(2240);
        Invoice origin = overdueInstallmentDueOn(d(2240, 3, 11));
        reconcile(origin, d(2240, 4, 1));

        payInFull(origin, d(2240, 4, 9), d(2240, 4, 9));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2240-03-12..2240-03-31:20:4000.00", "2240-04-01..2240-04-09:4:800.00");
        assertScenarioInvariants(d(2240, 4, 9));
    }

    @Test
    @Tag("D-294")
    @Tag("D-332")
    void aChargingChangeInsideThePaymentMonthGivesTheSameResultWhenTheEventIsRedeliveredLate() {
        arrange(2241);
        publishChargingDisabledFromTheFifthOfApril(2241);
        Invoice origin = overdueInstallmentDueOn(d(2241, 3, 11));
        reconcile(origin, d(2241, 4, 1));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2241, 4, 9), d(2241, 4, 9));
        MutableClock.INSTANCE.setDate(d(2241, 5, 20));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(origin.getId(),
                origin.getStudentCurrentAccountId(), InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), d(2241, 4, 9))));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2241-03-12..2241-03-31:20:4000.00", "2241-04-01..2241-04-09:4:800.00");
        assertScenarioInvariants(d(2241, 5, 20));
    }
}
