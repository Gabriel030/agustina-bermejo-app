package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-426")
@Tag("D-283")
@Tag("D-286")
class CohortPromotionReissueWindowIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final LocalDate WINDOW_END = LocalDate.of(2027, 4, 30);
    private static final LocalDate AFTER_WINDOW = LocalDate.of(2027, 5, 10);

    @Test
    void aBillingErrorReissueAfterTheWindowKeepsTheDiscountAndTheInstallmentNumber() {
        Long promotionId = acquirePromotion(3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        today(LocalDate.of(2027, 4, 20));
        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);

        today(AFTER_WINDOW);
        issue(lateInstallmentOf(APRIL));

        List<Map<String, Object>> aprilRows = discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(aprilRows).hasSize(2);
        assertThat(aprilRows.get(1).get("student_discount_id")).isEqualTo(promotionId);
        assertThat(aprilRows.get(1).get("consumption_released_at")).isNull();
        assertThat(aprilRows.get(1).get("installment_number")).isEqualTo(2);
        assertThat(aprilRows.get(1).get("installments_total")).isEqualTo(3);
        assertThat(amount(aprilRows.get(1).get("discount_amount"))).isEqualByComparingTo(amount(aprilRows.get(0).get("discount_amount")));
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aNewInstallmentAfterTheWindowStillGetsNoDiscountEvenIfAnotherChargeWasCancelledByError() {
        Long promotionId = acquirePromotion(3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        today(LocalDate.of(2027, 4, 20));
        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);

        today(AFTER_WINDOW);
        issue(lateInstallmentOf(MAY));

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MAY)).isEmpty();
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aFirstIssueAfterTheWindowGetsNoDiscountWhenNothingWasEverCancelled() {
        acquirePromotion(3);
        issueInstallment(MARCH);

        today(AFTER_WINDOW);
        issue(lateInstallmentOf(APRIL));

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL)).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aCancellationWithoutBillingErrorIsNotReissuedAndTheWindowStaysClosed() {
        Long promotionId = acquirePromotion(3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        today(LocalDate.of(2027, 4, 20));
        cancelAsDirector(april, CreditNoteReason.CANCELLATION);

        today(AFTER_WINDOW);
        InvoiceIssuingResult result = issuingService.issueInvoiceInNewTransaction(lateInstallmentOf(APRIL));

        assertThat(result).isNotInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL)).hasSize(1);
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aSystemCancellationThatReleasesTheChargeIsNotABillingErrorAndTheReissueAfterTheWindowGetsNoDiscount() {
        acquirePromotion(3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        today(LocalDate.of(2027, 4, 20));
        systemCancellation.cancelInvoiceWithoutDirector(april, SystemCancellationOrigin.ADMISSION_EXPIRED, "La admision venció sin pago");

        today(AFTER_WINDOW);
        issue(lateInstallmentOf(APRIL));

        List<Map<String, Object>> aprilRows = discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(aprilRows).hasSize(1);
        assertThat(aprilRows.get(0).get("consumption_released_at")).isNotNull();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aReissueCancelledByErrorAgainAfterTheWindowIsReissuedAgainWithTheSameDiscount() {
        Long promotionId = acquirePromotion(3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        today(LocalDate.of(2027, 4, 20));
        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);
        today(AFTER_WINDOW);
        Long reissued = issue(lateInstallmentOf(APRIL));
        cancelAsDirector(reissued, CreditNoteReason.BILLING_ERROR);

        today(AFTER_WINDOW.plusDays(5));
        issue(lateInstallmentOf(APRIL));

        List<Map<String, Object>> aprilRows = discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(aprilRows).hasSize(3);
        assertThat(aprilRows.get(2).get("consumption_released_at")).isNull();
        assertThat(aprilRows.get(2).get("installment_number")).isEqualTo(2);
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anExhaustedPromotionReissuedAfterTheWindowIsExhaustedAgain() {
        Long promotionId = acquirePromotion(2);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        today(LocalDate.of(2027, 4, 20));
        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);
        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");

        today(AFTER_WINDOW);
        issue(lateInstallmentOf(APRIL));

        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2);
        invariants.assertAllBillingInvariantsHold();
    }

    private InvoiceIssuingRequest lateInstallmentOf(YearMonth period) {
        InvoiceIssuingRequest base = installmentOf(learner, period);
        return new InvoiceIssuingRequest(base.studentId(), base.programEnrollmentId(), null, base.programId(), null, null, FeeType.MONTHLY_INSTALLMENT, period,
                LocalDate.now(MutableClock.INSTANCE).plusDays(10), base.pricingDate(), base.issuingReason(), base.paymentPlanSelectionId(), null, null, false, null, null, null,
                null, null, ACTOR);
    }

    private Long acquirePromotion(int installments) {
        return factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), installments, WINDOW_END);
    }

    private String statusOf(Long studentDiscountId) {
        return jdbc.queryForObject("select status from student_discount where id = ?", String.class, studentDiscountId);
    }

    private List<Integer> liveInstallmentNumbersOf(Long studentDiscountId) {
        return jdbc.queryForList("select installment_number from invoice_discount where student_discount_id = ? and consumption_released_at is null order by installment_number",
                Integer.class, studentDiscountId);
    }
}
