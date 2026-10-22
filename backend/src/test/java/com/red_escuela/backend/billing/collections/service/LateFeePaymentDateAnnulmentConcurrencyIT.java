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
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@SlowTest(reason = "Runs real threads against Postgres and shares the heavy Spring context of the late fee scenario hierarchy: when it is the first class of its shard it absorbs the whole context start-up")
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

    private static void waitMillis(long millis) {
        await().pollDelay(Duration.ofMillis(millis)).atMost(Duration.ofSeconds(5)).until(() -> true);
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

    @ParameterizedTest
    @CsvSource({"2551,0,0", "2552,0,250", "2553,250,0"})
    @Tag("D-332")
    void concurrentBackdatedPaymentAndMonthCloseNeverChargeADayAfterThePaymentDate(int year, long allocationDelayMillis, long closeDelayMillis) throws Exception {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        receiveMoney(origin, "100000.00", d(year, 3, 28));
        MutableClock.INSTANCE.setDate(d(year, 4, 1));
        CountDownLatch start = new CountDownLatch(1);

        runTogether(List.of(() -> {
            start.await();
            waitMillis(allocationDelayMillis);
            allocateOn(origin, d(year, 4, 1));
            return null;
        }, () -> {
            start.await();
            waitMillis(closeDelayMillis);
            return accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(year, 4, 1));
        }), start);
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select coalesce(sum(c.charged_days), 0) - coalesce((select sum(k.corrected_days) from late_fee_charge_correction k "
                + "join late_fee_charge c2 on c2.id = k.late_fee_charge_id where c2.origin_invoice_id = ?), 0) from late_fee_charge c where c.origin_invoice_id = ?",
                Long.class, origin.getId(), origin.getId())).isEqualTo(17L);
        assertThat(chargesOf(origin)).isNotEmpty();
        assertScenarioInvariants(d(year, 4, 2));
    }
}
