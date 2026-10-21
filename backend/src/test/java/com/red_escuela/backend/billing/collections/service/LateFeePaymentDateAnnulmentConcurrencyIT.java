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
import org.mockito.AdditionalAnswers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class LateFeePaymentDateAnnulmentConcurrencyIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private LateFeeSettlementCorrectionService settlementCorrectionService;
    @Autowired
    private InvoiceSettlementQueryService settlementQuery;
    @Autowired
    private LateFeeOnInvoiceSettledListener settledListener;
    @Autowired
    private LateFeeCorrectionIssuer issuer;

    private LateFeeCorrectionIssuer issuerTarget;
    private LateFeeSettlementCorrectionService serviceTarget;
    private LateFeeChargeCorrectionRepository originalCorrectionRepository;
    private LateFeeCorrectionIssuer originalIssuer;
    private LateFeeChargeCorrectionRepository correctionRepositorySpy;
    private LateFeeCorrectionIssuer issuerSpy;

    @BeforeEach
    void installSpies() {
        issuerTarget = AopTestUtils.getUltimateTargetObject(issuer);
        serviceTarget = AopTestUtils.getUltimateTargetObject(settlementCorrectionService);
        originalCorrectionRepository = (LateFeeChargeCorrectionRepository) ReflectionTestUtils.getField(issuerTarget, "correctionRepository");
        originalIssuer = (LateFeeCorrectionIssuer) ReflectionTestUtils.getField(serviceTarget, "issuer");
        correctionRepositorySpy = mock(LateFeeChargeCorrectionRepository.class, AdditionalAnswers.delegatesTo(originalCorrectionRepository));
        issuerSpy = spy(issuerTarget);
        ReflectionTestUtils.setField(issuerTarget, "correctionRepository", correctionRepositorySpy);
    }

    @AfterEach
    void restore() {
        MutableClock.INSTANCE.reset();
        ReflectionTestUtils.setField(issuerTarget, "correctionRepository", originalCorrectionRepository);
        ReflectionTestUtils.setField(serviceTarget, "issuer", originalIssuer);
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

    @Test
    @Tag("D-378")
    void aFailedAnnulmentIsPropagatedSoTheEventIsRetriedAndTheRetryAnnulsOnce() {
        arrange(2409);
        publishDefaults(2409);
        Invoice origin = overdueInstallmentDueOn(d(2409, 3, 11));
        reconcile(origin, d(2409, 4, 2));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2409, 3, 28), d(2409, 4, 2));
        InvoiceSettlement settlement = settlementQuery.findCurrentSettlementOfInvoice(origin.getId()).orElseThrow();
        MutableClock.INSTANCE.setDate(d(2409, 4, 2));
        doThrow(new IllegalStateException("boom")).when(issuerSpy).correctLateFeeChargeAfterPaymentDateInNewTransaction(anyLong(), any());
        ReflectionTestUtils.setField(serviceTarget, "issuer", issuerSpy);

        assertThatThrownBy(() -> settlementCorrectionService.correctLateFeesChargedAfterThePaymentOf(origin.getId(), settlement)).isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(serviceTarget, "issuer", issuerTarget);
        assertThat(settlementCorrectionService.correctLateFeesChargedAfterThePaymentOf(origin.getId(), settlement)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ? and reason = 'LATE_FEE_ANNULMENT'", Long.class, debitNoteId)).isEqualTo(1L);
    }
}
