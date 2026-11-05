package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-129")
@Tag("D-157")
@Tag("D-158")
@Tag("D-388")
@Tag("D-238")
@Tag("D-097")
@Tag("D-284")
@Tag("D-384")
@Tag("D-389")
@Tag("D-401")
@Tag("D-402")
class StudentDiscountPoliciesTest {
    private static final YearMonth NEXT = YearMonth.of(2027, 4);
    private static final MonetaryAdjustment HALF = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50")));

    private StudentDiscountGrantCheck check(DiscountType type, InvoiceType invoiceType, MonetaryAdjustment calculation, YearMonth from, YearMonth to) {
        return new StudentDiscountGrantCheck(type, invoiceType, calculation, null, Currency.ARS, from, to, NEXT, false, "motivo");
    }

    private StudentDiscountGrantCheck scholarship(YearMonth from, YearMonth to) {
        return check(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, from, to);
    }

    private static String code(PolicyVerdict verdict) {
        return verdict.allowed() ? "ALLOWED" : verdict.code();
    }

    @Test
    void aScholarshipOnlyTargetsMonthlyInstallmentsAndNoDiscountTargetsALateFee() {
        StudentDiscountInvoiceTypePolicy policy = new StudentDiscountInvoiceTypePolicy();

        assertThat(code(policy.evaluateRule(check(DiscountType.SCHOLARSHIP, InvoiceType.ENROLLMENT_FEE, HALF, NEXT, NEXT)))).isEqualTo("DISCOUNT_INVOICE_TYPE_MISMATCH");
        assertThat(code(policy.evaluateRule(check(DiscountType.SCHOLARSHIP, InvoiceType.ANNUAL_UPFRONT, HALF, NEXT, NEXT)))).isEqualTo("DISCOUNT_INVOICE_TYPE_MISMATCH");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.LATE_FEE, HALF, NEXT, NEXT)))).isEqualTo("DISCOUNT_INVOICE_TYPE_MISMATCH");
        assertThat(code(policy.evaluateRule(scholarship(NEXT, NEXT)))).isEqualTo("ALLOWED");
        for (InvoiceType type : InvoiceType.values()) {
            if (type != InvoiceType.LATE_FEE) {
                assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, type, HALF, NEXT, NEXT)))).isEqualTo("ALLOWED");
            }
        }
    }

    @Test
    void theStartMustNotBeBeforeTheNextInstallmentButTheBorderIsAccepted() {
        ScholarshipStartsAtNextInstallmentPolicy policy = new ScholarshipStartsAtNextInstallmentPolicy();

        assertThat(code(policy.evaluateRule(scholarship(YearMonth.of(2027, 3), YearMonth.of(2027, 6))))).isEqualTo("SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT");
        assertThat(code(policy.evaluateRule(scholarship(NEXT, YearMonth.of(2027, 6))))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(scholarship(NEXT.plusMonths(1), YearMonth.of(2027, 6))))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, HALF, YearMonth.of(2027, 3), YearMonth.of(2027, 3))))).isEqualTo("ALLOWED");
    }

    @Test
    void theRangeNeedsBothEndsInOrderAndASingleChargeCoversOnePeriod() {
        StudentDiscountPeriodRangePolicy policy = new StudentDiscountPeriodRangePolicy();

        assertThat(code(policy.evaluateRule(scholarship(null, NEXT)))).isEqualTo("INVALID_SCHOLARSHIP_PERIOD");
        assertThat(code(policy.evaluateRule(scholarship(NEXT, null)))).isEqualTo("INVALID_SCHOLARSHIP_PERIOD");
        assertThat(code(policy.evaluateRule(scholarship(NEXT.plusMonths(2), NEXT)))).isEqualTo("INVALID_SCHOLARSHIP_PERIOD");
        assertThat(code(policy.evaluateRule(scholarship(NEXT, NEXT)))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, HALF, NEXT, NEXT.plusMonths(1))))).isEqualTo("INVALID_SCHOLARSHIP_PERIOD");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.ENROLLMENT_FEE, HALF, NEXT, NEXT)))).isEqualTo("ALLOWED");
    }

    @Test
    void aFixedAmountAndTheCapMustBeInTheCurrencyOfTheEnrollment() {
        StudentDiscountCurrencyPolicy policy = new StudentDiscountCurrencyPolicy();
        MonetaryAdjustment usd = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("10.00"), Currency.USD));
        MonetaryAdjustment ars = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("10000.00"), Currency.ARS));
        NonNegativeMoney usdCap = NonNegativeMoney.of(new BigDecimal("5.00"), Currency.USD);

        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, usd, NEXT, NEXT)))).isEqualTo("CURRENCY_MISMATCH");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, ars, NEXT, NEXT)))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, HALF, NEXT, NEXT)))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(new StudentDiscountGrantCheck(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, usdCap, Currency.ARS, NEXT, NEXT,
                NEXT, false, "motivo")))).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void aScholarshipIsRejectedWhenTheAnnualUpfrontCoversTheRangeButAnIndividualDiscountIsNot() {
        ScholarshipNotAvailableWithAnnualUpfrontPolicy policy = new ScholarshipNotAvailableWithAnnualUpfrontPolicy();

        assertThat(code(policy.evaluateRule(new StudentDiscountGrantCheck(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT,
                NEXT, true, "motivo")))).isEqualTo("SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT");
        assertThat(code(policy.evaluateRule(scholarship(NEXT, NEXT)))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(new StudentDiscountGrantCheck(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT,
                NEXT, true, "motivo")))).isEqualTo("ALLOWED");
    }

    @Test
    void theCoherencePolicyRejectsZeroValuesAndReasonsBeyondTheStoredLength() {
        StudentDiscountCoherencePolicy policy = new StudentDiscountCoherencePolicy();
        MonetaryAdjustment zeroPercent = new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.ZERO));
        MonetaryAdjustment zeroAmount = new MonetaryAdjustment.Nominal(Money.zero(Currency.ARS));
        StudentDiscountGrantCheck longReason = new StudentDiscountGrantCheck(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT,
                NEXT, false, "x".repeat(401));
        StudentDiscountGrantCheck maxReason = new StudentDiscountGrantCheck(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT,
                NEXT, false, "x".repeat(400));

        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, zeroPercent, NEXT, NEXT)))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, zeroAmount, NEXT, NEXT)))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(check(DiscountType.INDIVIDUAL, InvoiceType.MONTHLY_INSTALLMENT, null, NEXT, NEXT)))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(longReason))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(maxReason))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(check(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, NEXT, NEXT)))).isEqualTo("ALLOWED");
    }

    @Test
    void theRevocationReasonIsRequiredAndBoundedByWhatTheAuthorizationRequestCanStore() {
        RevocationReasonRequiredPolicy policy = new RevocationReasonRequiredPolicy();

        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck(null)))).isEqualTo("REVOCATION_REASON_REQUIRED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("   ")))).isEqualTo("REVOCATION_REASON_REQUIRED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("x".repeat(401))))).isEqualTo("REVOCATION_REASON_REQUIRED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("x".repeat(400))))).isEqualTo("ALLOWED");
    }

    @Test
    void theDefinitionPolicyMapsEachFaultToItsCode() {
        StudentDiscountDefinitionPolicy policy = new StudentDiscountDefinitionPolicy();

        for (StudentDiscountGrantCheck.DefinitionFault fault : StudentDiscountGrantCheck.DefinitionFault.values()) {
            StudentDiscountGrantCheck check = new StudentDiscountGrantCheck(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT,
                    NEXT, false, "motivo", fault);
            String expected = switch (fault) {
                case NONE -> "ALLOWED";
                case CLOSED -> "DISCOUNT_DEFINITION_CLOSED";
                default -> "INVALID_STUDENT_DISCOUNT";
            };
            assertThat(code(policy.evaluateRule(check))).as(fault.name()).isEqualTo(expected);
        }
    }

    @Test
    void aProposalIsRejectedOnOrAfterItsDeadlineAndAcceptedTheDayBefore() {
        ProposalNotExpiredPolicy policy = new ProposalNotExpiredPolicy();
        java.time.LocalDate deadline = java.time.LocalDate.of(2027, 3, 1);

        assertThat(code(policy.evaluateRule(withDeadline(deadline, java.time.LocalDate.of(2027, 2, 28))))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(withDeadline(deadline, deadline)))).isEqualTo("STUDENT_DISCOUNT_PROPOSAL_EXPIRED");
        assertThat(code(policy.evaluateRule(withDeadline(deadline, java.time.LocalDate.of(2027, 3, 5))))).isEqualTo("STUDENT_DISCOUNT_PROPOSAL_EXPIRED");
        assertThat(code(policy.evaluateRule(withDeadline(null, java.time.LocalDate.of(2027, 3, 5))))).isEqualTo("ALLOWED");
    }

    @Test
    void aRevocationProposalIsRejectedOnTheFirstDayOfTheNextMonthAndAcceptedTheDayBefore() {
        RevocationProposalNotExpiredPolicy policy = new RevocationProposalNotExpiredPolicy();
        java.time.LocalDate deadline = java.time.LocalDate.of(2027, 4, 1);

        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("motivo", deadline, java.time.LocalDate.of(2027, 3, 31))))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("motivo", deadline, deadline)))).isEqualTo("STUDENT_DISCOUNT_PROPOSAL_EXPIRED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("motivo", null, deadline)))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(new StudentDiscountRevocationCheck("motivo")))).isEqualTo("ALLOWED");
    }

    @Test
    void anOverlapWithAnActiveDiscountIsRejectedAndNoOverlapIsAccepted() {
        StudentDiscountNoOverlapPolicy policy = new StudentDiscountNoOverlapPolicy();
        StudentDiscountGrantCheck.OverlappingDiscount overlap = new StudentDiscountGrantCheck.OverlappingDiscount(7L, YearMonth.of(2027, 3), YearMonth.of(2027, 12));

        assertThat(code(policy.evaluateRule(withOverlap(overlap, false)))).isEqualTo("STUDENT_DISCOUNT_OVERLAP");
        assertThat(policy.evaluateRule(withOverlap(overlap, false)).toString()).contains("n.º 7");
        assertThat(code(policy.evaluateRule(withOverlap(null, false)))).isEqualTo("ALLOWED");
    }

    @Test
    void anIssuedEnrollmentFeeBlocksTheGrantAndAnUnissuedOneDoesNot() {
        EnrollmentFeeDiscountBeforeIssuePolicy policy = new EnrollmentFeeDiscountBeforeIssuePolicy();

        assertThat(code(policy.evaluateRule(withOverlap(null, true)))).isEqualTo("ENROLLMENT_FEE_ALREADY_ISSUED");
        assertThat(code(policy.evaluateRule(withOverlap(null, false)))).isEqualTo("ALLOWED");
    }

    private StudentDiscountGrantCheck withOverlap(StudentDiscountGrantCheck.OverlappingDiscount overlap, boolean enrollmentFeeIssued) {
        return new StudentDiscountGrantCheck(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT, NEXT, false, "motivo",
                StudentDiscountGrantCheck.DefinitionFault.NONE, null, null, overlap, enrollmentFeeIssued);
    }

    @Test
    void theCoherencePolicyRejectsAnUnsupportedTypeAZeroAdjustmentAZeroCapAndAnInvalidReason() {
        StudentDiscountCoherencePolicy policy = new StudentDiscountCoherencePolicy();
        MonetaryAdjustment zeroPercent = new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.ZERO));
        MonetaryAdjustment zeroAmount = new MonetaryAdjustment.Nominal(Money.of(BigDecimal.ZERO, Currency.ARS));

        assertThat(code(policy.evaluateRule(coherence(DiscountType.PROMOTION, HALF, null, "motivo")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, null, null, "motivo")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, zeroPercent, null, "motivo")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, zeroAmount, null, "motivo")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, HALF, NonNegativeMoney.zero(Currency.ARS), "motivo")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, HALF, null, null)))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, HALF, null, "  ")))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, HALF, null, "x".repeat(401))))).isEqualTo("INVALID_STUDENT_DISCOUNT");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.SCHOLARSHIP, HALF, null, "x".repeat(400))))).isEqualTo("ALLOWED");
        assertThat(code(policy.evaluateRule(coherence(DiscountType.INDIVIDUAL, HALF, NonNegativeMoney.of(new BigDecimal("1.00"), Currency.ARS), "motivo")))).isEqualTo("ALLOWED");
    }

    private StudentDiscountGrantCheck coherence(DiscountType type, MonetaryAdjustment calculation, NonNegativeMoney cap, String reason) {
        return new StudentDiscountGrantCheck(type, InvoiceType.MONTHLY_INSTALLMENT, calculation, cap, Currency.ARS, NEXT, NEXT, NEXT, false, reason);
    }

    private StudentDiscountGrantCheck withDeadline(java.time.LocalDate deadline, java.time.LocalDate today) {
        return new StudentDiscountGrantCheck(DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT, HALF, null, Currency.ARS, NEXT, NEXT, NEXT, false, "motivo",
                StudentDiscountGrantCheck.DefinitionFault.NONE, deadline, today);
    }
}
