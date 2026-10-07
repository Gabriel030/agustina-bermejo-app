package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssuingPoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    private static InvoiceIssuingContext context(FeeType type, InvoiceIssuingReason reason, LocalDate due, Long templateId, Integer slot, boolean entry,
                                           Money amount, Long existing, Percentage entryDiscount) {
        InvoiceIssuingRequest request = new InvoiceIssuingRequest(new InvoiceRecipient.CourseApplicantRecipient(null), type, YearMonth.from(TODAY), due, reason, null,
                templateId, slot, entry, null, null, null, null);
        return new InvoiceIssuingContext(request, TODAY, Currency.ARS, amount, null, existing, entryDiscount, null);
    }

    private static InvoiceIssuingContext simple(FeeType type, InvoiceIssuingReason reason) {
        return context(type, reason, TODAY, null, null, false, ars(100), null, null);
    }

    @Test
    @Tag("D-057")
    void dueDateEqualToTodayIsAllowedAndYesterdayIsRejected() {
        DueDateNotBeforeIssueDatePolicy policy = new DueDateNotBeforeIssueDatePolicy();

        assertThat(policy.evaluateRule(context(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null, false, ars(1), null, null)).allowed()).isTrue();
        var denial = policy.evaluateRule(context(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY.minusDays(1), null, null, false, ars(1), null, null));
        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
    }

    @Test
    void anEnrollmentFeeOfAProgramNeedsTemplateAndSlot() {
        EnrollmentFeeSlotRequiredPolicy policy = new EnrollmentFeeSlotRequiredPolicy();
        InvoiceIssuingRequest withoutSlot = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramApplicantRecipient(null, null), FeeType.ENROLLMENT_FEE, YearMonth.from(TODAY), TODAY,
                InvoiceIssuingReason.PROGRAM_ADMISSION, null, 5L, null, true, null, null, null, null);
        InvoiceIssuingRequest complete = new InvoiceIssuingRequest(new InvoiceRecipient.ProgramApplicantRecipient(null, null), FeeType.ENROLLMENT_FEE, YearMonth.from(TODAY), TODAY,
                InvoiceIssuingReason.PROGRAM_ADMISSION, null, 5L, 1, true, null, null, null, null);

        assertThat(policy.evaluateRule(new InvoiceIssuingContext(withoutSlot, TODAY, Currency.ARS, ars(1), null, null, null, null)).code())
                .isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(policy.evaluateRule(new InvoiceIssuingContext(complete, TODAY, Currency.ARS, ars(1), null, null, null, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(simple(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.COURSE_MONTHLY_RUN)).allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = FeeType.class, names = {"MONTHLY_INSTALLMENT", "ENROLLMENT_FEE"})
    void anAdHocChargeCannotUseARegularFeeType(FeeType type) {
        assertThat(new ReasonFeeTypePolicy().evaluateRule(simple(type, InvoiceIssuingReason.AD_HOC_CHARGE)).code()).isEqualTo("FEE_TYPE_NOT_ALLOWED_FOR_REASON");
    }

    @ParameterizedTest
    @EnumSource(value = FeeType.class, names = {"EXAM_FEE", "RETAKEN_SUBJECT_FEE", "DEGREE_ISSUANCE_FEE", "ADMINISTRATIVE_FEE"})
    void anAdHocChargeAcceptsOnlyTheAdHocFeeTypes(FeeType type) {
        ReasonFeeTypePolicy policy = new ReasonFeeTypePolicy();

        assertThat(policy.evaluateRule(simple(type, InvoiceIssuingReason.AD_HOC_CHARGE)).allowed()).isTrue();
        assertThat(policy.evaluateRule(simple(type, InvoiceIssuingReason.MONTHLY_RUN)).allowed()).isFalse();
    }

    @Test
    void legacyIsNeverAcceptedForNewCharges() {
        for (FeeType type : FeeType.values()) {
            assertThat(new ReasonFeeTypePolicy().evaluateRule(simple(type, InvoiceIssuingReason.LEGACY)).allowed()).isFalse();
        }
    }

    @Test
    void admissionReasonsRequireTheirOwnFeeType() {
        ReasonFeeTypePolicy policy = new ReasonFeeTypePolicy();

        assertThat(policy.evaluateRule(simple(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.PROGRAM_ADMISSION)).allowed()).isTrue();
        assertThat(policy.evaluateRule(simple(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.PROGRAM_ADMISSION)).allowed()).isFalse();
        assertThat(policy.evaluateRule(simple(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.COURSE_ADMISSION)).allowed()).isTrue();
        assertThat(policy.evaluateRule(simple(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.COURSE_ADMISSION)).allowed()).isFalse();
    }

    @Test
    void alreadyIssuedIsRejected() {
        NotAlreadyIssuedPolicy policy = new NotAlreadyIssuedPolicy();

        assertThat(policy.evaluateRule(context(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null, false, ars(1), 7L, null)).code()).isEqualTo("ALREADY_ISSUED");
        assertThat(policy.evaluateRule(simple(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN)).allowed()).isTrue();
    }

    @Test
    void currencyMustMatchTheOffering() {
        OfferingCurrencyPolicy policy = new OfferingCurrencyPolicy();

        assertThat(policy.evaluateRule(simple(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(FeeType.MONTHLY_INSTALLMENT, InvoiceIssuingReason.MONTHLY_RUN, TODAY, null, null, false,
                Money.of(BigDecimal.TEN, Currency.USD), null, null)).code()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void entryDiscountAppliesOnlyToEntryFeesThatDoNotComeFromAdmission() {
        EntryFeeDiscountPolicy policy = new EntryFeeDiscountPolicy();
        Percentage fifty = Percentage.ofPoints(BigDecimal.valueOf(50));

        assertThat(policy.evaluateRule(context(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 1L, 1, true, ars(1), null, fifty)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.PROGRAM_ADMISSION, TODAY, 1L, 1, true, ars(1), null, fifty)).allowed()).isFalse();
        assertThat(policy.evaluateRule(context(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 1L, 1, false, ars(1), null, fifty)).allowed()).isFalse();
        assertThat(policy.evaluateRule(context(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 1L, 1, true, ars(1), null, Percentage.ofPoints(BigDecimal.ZERO))).allowed()).isFalse();
        assertThat(policy.evaluateRule(context(FeeType.ENROLLMENT_FEE, InvoiceIssuingReason.MONTHLY_RUN, TODAY, 1L, 1, true, ars(1), null, null)).allowed()).isFalse();
    }

    @Test
    void promotionBenefitCapIsExhaustedAtTheScopeCount() {
        PromotionBenefitCapPolicy policy = new PromotionBenefitCapPolicy();

        assertThat(policy.evaluateRule(new PromotionBenefitCapPolicy.Usage(null, 100)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PromotionBenefitCapPolicy.Usage(3, 2)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new PromotionBenefitCapPolicy.Usage(3, 3)).code()).isEqualTo("PROMOTION_BENEFIT_EXHAUSTED");
    }
}
