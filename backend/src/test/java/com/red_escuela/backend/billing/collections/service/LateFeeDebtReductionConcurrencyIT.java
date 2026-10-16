package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-377")
@Tag("D-332")
class LateFeeDebtReductionConcurrencyIT extends AbstractLateFeeScenarioIT {

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
    void theSettlingCreditNoteAndTheMonthCloseRunningTogetherIssueOneDebitNotePerMonthAndNoOverlap() throws Exception {
        arrange(2310);
        publishDefaults(2310);
        Invoice origin = overdueInstallmentDueOn(d(2310, 3, 11));
        MutableClock.INSTANCE.setDate(d(2310, 4, 1));

        runInParallel(List.of(() -> {
            settleByDebtReductionCreditNote(origin, d(2310, 4, 1));
            return null;
        }, () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2310, 4, 1))));
        awaitEventsDrained();
        reconcile(origin, d(2310, 4, 1));

        assertThat(summaryOf(origin)).containsExactly("2310-03-12..2310-03-31:20:4000.00", "2310-04-01..2310-04-01:1:200.00");
        assertScenarioInvariants(d(2310, 4, 1));
    }
}
