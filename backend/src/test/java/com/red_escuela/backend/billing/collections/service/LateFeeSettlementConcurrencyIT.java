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

class LateFeeSettlementConcurrencyIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private InvoiceRepository repository;

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
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
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
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
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
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
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
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
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
    }
}
