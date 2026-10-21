package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.InvoiceSettlement;
import com.red_escuela.backend.billing.account.service.InvoiceSettlementQueryService;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeCorrectionRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

class LateFeePaymentDateAnnulmentConcurrencyIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private LateFeeSettlementCorrectionService settlementCorrectionService;
    @Autowired
    private InvoiceSettlementQueryService settlementQuery;
    @Autowired
    private LateFeeOnInvoiceSettledListener settledListener;
    @MockitoSpyBean
    private LateFeeChargeCorrectionRepository correctionRepositorySpy;

    @AfterEach
    void restore() {
        MutableClock.INSTANCE.reset();
        reset(correctionRepositorySpy);
    }

    private void runTogether(List<Callable<Object>> tasks, CountDownLatch start) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-378")
    void annulmentIsIssuedOnceAcrossRedeliveryAndConcurrentReconciliation() throws Exception {
        arrange(2408);
        publishDefaults(2408);
        Invoice origin = overdueInstallmentDueOn(d(2408, 3, 11));
        reconcile(origin, d(2408, 4, 2));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2408, 3, 28), d(2408, 4, 2));
        InvoiceSettlement settlement = settlementQuery.findCurrentSettlementOfInvoice(origin.getId()).orElseThrow();
        MutableClock.INSTANCE.setDate(d(2408, 4, 2));
        doReturn(List.of()).when(correctionRepositorySpy).findByLateFeeChargeIdOrderByFirstCorrectedDay(anyLong());
        CountDownLatch start = new CountDownLatch(1);
        InvoicePaidDomainEvent redelivered = InvoicePaidDomainEvent.paidFor(origin.getId(), origin.getStudentCurrentAccountId(),
                InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), d(2408, 4, 2));

        runTogether(List.of(() -> {
            start.await();
            return settlementCorrectionService.correctLateFeesChargedAfterThePaymentOf(origin.getId(), settlement);
        }, () -> {
            start.await();
            return settlementCorrectionService.correctLateFeesChargedAfterThePaymentOf(origin.getId(), settlement);
        }, () -> {
            start.await();
            settledListener.reconcileLateFeesOfSettledInvoice(redelivered);
            return null;
        }), start);
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ? and reason = 'LATE_FEE_ANNULMENT'", Long.class, debitNoteId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select outstanding_amount from invoice where id = ?", java.math.BigDecimal.class, debitNoteId)).isEqualByComparingTo("3400.00");
        assertScenarioInvariants(d(2408, 4, 2));
    }
}
