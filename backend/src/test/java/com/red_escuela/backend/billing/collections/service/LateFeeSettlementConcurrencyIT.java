package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import com.red_escuela.backend.testsupport.SlowTest;
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

@SlowTest(reason = "Real payments, reversals and async listeners over a shared Postgres; each scenario needs its own data and the class shares one Spring context, so it cannot be split without a new context")
class LateFeeSettlementConcurrencyIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private InvoiceRepository repository;
    @Autowired
    private MeterRegistry meterRegistry;

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

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(again);
            eventPublisher.publishEvent(again);
        });
        awaitEventsDrained();
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
        assertThat(repository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2235, 2, 1), 10)).doesNotContain(origin.getId());
        assertScenarioInvariants(d(2235, 3, 26));
    }

    @Test
    @Tag("D-332")
    @Tag("D-243")
    void aSettlementWithExcessWhoseListenerNeverRanIsRecordedOnceByTheNightlyRun() {
        arrange(2236);
        publishDefaults(2236);
        Invoice origin = overdueInstallmentDueOn(d(2236, 3, 11));
        reconcile(origin, d(2236, 4, 2));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2236, 3, 28), d(2236, 4, 2));
        double before = meterRegistry.counter("billing_late_fees_overcharged_days_total").count();

        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2236, 4, 3));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2236, 4, 4));

        assertThat(first.settlementCandidates()).isEqualTo(1);
        assertThat(second.settlementCandidates()).isZero();
        assertThat(meterRegistry.counter("billing_late_fees_overcharged_days_total").count() - before).isEqualTo(3.0);
        assertThat(summaryOf(origin)).containsExactly("2236-03-12..2236-03-31:20:4000.00");
        assertScenarioInvariants(d(2236, 4, 4));
    }

    @Test
    @Tag("D-331")
    void aPaymentInsideTheGraceIsNotACandidateOfTheNightlyRecovery() {
        arrange(2237);
        publish(10, "0.002", 10, true, true, d(2237, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2237, 3, 11));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2237, 3, 20), d(2237, 3, 20));

        assertThat(repository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2237, 3, 1), 50)).doesNotContain(origin.getId());
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
}
