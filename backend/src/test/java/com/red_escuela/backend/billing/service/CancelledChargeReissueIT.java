package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-202")
@Tag("D-245")
class CancelledChargeReissueIT extends AbstractInvoiceSequenceTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final YearMonth MAY = YearMonth.of(2026, 5);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CancelInvoiceOperation cancelOperation;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Long enrolledInApril() {
        Career career = newCareer();
        Year y26 = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(y26, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 20));
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, APRIL, date(2026, 4, 20));
    }

    private void cancel(Invoice invoice, CreditNoteReason reason) {
        Long director = transactionTemplate.execute(status -> newPerson().getId());
        pipeline.runOperation(cancelOperation, CancelInvoiceCommand.byPerson(invoice.getId(), reason, "Cancelación de prueba", director, true));
    }

    private long liveInvoices(Long enrollmentId, FeeType type) {
        return invoices(enrollmentId, type).stream().filter(i -> !i.getPrimaryLine().getChargeKeyReleased() && i.getPaymentStatus() != InvoicePaymentStatus.CANCELLED).count();
    }

    @Test
    @Tag("B2-14")
    @Tag("D-009")
    void billingErrorInstallmentIsReissuedWithTheSameKey() {
        Long enrollment = enrolledInApril();
        job.run(APRIL);
        Invoice april = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0);
        String key = april.getPrimaryLine().getChargeKey();

        cancel(april, CreditNoteReason.BILLING_ERROR);
        job.run(APRIL);

        List<Invoice> installments = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT);
        assertThat(installments).hasSize(2);
        assertThat(installments).filteredOn(i -> i.getPrimaryLine().getChargeKeyReleased()).hasSize(1);
        assertThat(installments).filteredOn(i -> !i.getPrimaryLine().getChargeKeyReleased()).singleElement()
                .satisfies(live -> assertThat(live.getPrimaryLine().getChargeKey()).isEqualTo(key))
                .satisfies(live -> assertThat(live.getPaymentStatus()).isNotEqualTo(InvoicePaymentStatus.CANCELLED));
        invariantChecker.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-15")
    void cancellationInstallmentIsNotReissuedButTheNextMonthIs() {
        Long enrollment = enrolledInApril();
        job.run(APRIL);
        Invoice april = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0);

        cancel(april, CreditNoteReason.CANCELLATION);
        job.run(APRIL);
        today(date(2026, 5, 5));
        job.run(MAY);

        assertThat(periods(enrollment, FeeType.MONTHLY_INSTALLMENT)).containsExactly(APRIL, MAY);
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0).getPrimaryLine().getChargeKeyReleased()).isFalse();
        invariantChecker.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-17")
    @Tag("D-166")
    void enrollmentFeeReissueFollowsTheReason() {
        Long byError = enrolledInApril();
        Long byDirection = enrolledInApril();
        issueEntryFee(byError);
        issueEntryFee(byDirection);
        Invoice errorFee = invoices(byError, FeeType.ENROLLMENT_FEE).get(0);
        Invoice directionFee = invoices(byDirection, FeeType.ENROLLMENT_FEE).get(0);

        cancel(errorFee, CreditNoteReason.BILLING_ERROR);
        cancel(directionFee, CreditNoteReason.CANCELLATION);
        job.run(APRIL);
        issueEntryFee(byError);
        issueEntryFee(byDirection);

        assertThat(liveInvoices(byError, FeeType.ENROLLMENT_FEE)).isEqualTo(1L);
        assertThat(liveInvoices(byDirection, FeeType.ENROLLMENT_FEE)).isZero();
        invariantChecker.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-27")
    @Tag("D-009")
    void cancellationAndIssuanceOfTheSamePeriodNeverLeaveTwoLiveKeys() throws Exception {
        Long enrollment = enrolledInApril();
        job.run(APRIL);
        Invoice april = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancelling = executor.submit(() -> {
                start.await();
                cancel(april, CreditNoteReason.BILLING_ERROR);
                return null;
            });
            Future<?> issuing = executor.submit(() -> {
                start.await();
                job.run(APRIL);
                return null;
            });
            start.countDown();
            cancelling.get(60, TimeUnit.SECONDS);
            issuing.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        job.run(APRIL);

        assertThat(liveInvoices(enrollment, FeeType.MONTHLY_INSTALLMENT)).isEqualTo(1L);
        invariantChecker.assertAllBillingInvariantsHold();
    }
}
