package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssuanceContext;
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

class IssuancePoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    private static IssuanceContext context(FeeType type, IssueReason reason, LocalDate due, Long templateId, Integer slot, boolean entry,
                                           Money amount, Long existing, Percentage entryDiscount) {
        ChargeRequest request = new ChargeRequest(new ChargeTarget.CourseApplicant(null), type, YearMonth.from(TODAY), due, reason, null,
                templateId, slot, entry, null, null, null, null);
        return new IssuanceContext(request, TODAY, Currency.ARS, amount, null, existing, entryDiscount, null);
    }

    private static IssuanceContext simple(FeeType type, IssueReason reason) {
        return context(type, reason, TODAY, null, null, false, ars(100), null, null);
    }

    @Test
    @Tag("D-057")
    void dueDateEqualToTodayIsAllowedAndYesterdayIsRejected() {
        DueDateNotBeforeIssueDatePolicy policy = new DueDateNotBeforeIssueDatePolicy();

        assertThat(policy.evaluate(context(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB, TODAY, null, null, false, ars(1), null, null)).allowed()).isTrue();
        var denial = policy.evaluate(context(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB, TODAY.minusDays(1), null, null, false, ars(1), null, null));
        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
    }

    @Test
    void anEnrollmentFeeOfAProgramNeedsTemplateAndSlot() {
        EnrollmentFeeSlotRequiredPolicy policy = new EnrollmentFeeSlotRequiredPolicy();
        ChargeRequest withoutSlot = new ChargeRequest(new ChargeTarget.ProgramApplicant(null, null), FeeType.ENROLLMENT_FEE, YearMonth.from(TODAY), TODAY,
                IssueReason.ADMISSION, null, 5L, null, true, null, null, null, null);
        ChargeRequest complete = new ChargeRequest(new ChargeTarget.ProgramApplicant(null, null), FeeType.ENROLLMENT_FEE, YearMonth.from(TODAY), TODAY,
                IssueReason.ADMISSION, null, 5L, 1, true, null, null, null, null);

        assertThat(policy.evaluate(new IssuanceContext(withoutSlot, TODAY, Currency.ARS, ars(1), null, null, null, null)).code())
                .isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(policy.evaluate(new IssuanceContext(complete, TODAY, Currency.ARS, ars(1), null, null, null, null)).allowed()).isTrue();
        assertThat(policy.evaluate(simple(FeeType.RECURRING_FEE, IssueReason.COURSE_MONTHLY)).allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = FeeType.class, names = {"RECURRING_FEE", "ENROLLMENT_FEE", "SUBSCRIPTION_FEE"})
    void anAdHocChargeCannotUseARegularFeeType(FeeType type) {
        assertThat(new ReasonFeeTypePolicy().evaluate(simple(type, IssueReason.AD_HOC)).code()).isEqualTo("FEE_TYPE_NOT_ALLOWED_FOR_REASON");
    }

    @ParameterizedTest
    @EnumSource(value = FeeType.class, names = {"EXAM_RIGHT", "RETAKEN_SUBJECT_FEE", "DEGREE_ISSUANCE", "ADMINISTRATIVE_FEE"})
    void anAdHocChargeAcceptsOnlyTheAdHocFeeTypes(FeeType type) {
        ReasonFeeTypePolicy policy = new ReasonFeeTypePolicy();

        assertThat(policy.evaluate(simple(type, IssueReason.AD_HOC)).allowed()).isTrue();
        assertThat(policy.evaluate(simple(type, IssueReason.MONTHLY_JOB)).allowed()).isFalse();
    }

    @Test
    void legacyIsNeverAcceptedForNewCharges() {
        for (FeeType type : FeeType.values()) {
            assertThat(new ReasonFeeTypePolicy().evaluate(simple(type, IssueReason.LEGACY)).allowed()).isFalse();
        }
    }

    @Test
    void admissionReasonsRequireTheirOwnFeeType() {
        ReasonFeeTypePolicy policy = new ReasonFeeTypePolicy();

        assertThat(policy.evaluate(simple(FeeType.ENROLLMENT_FEE, IssueReason.ADMISSION)).allowed()).isTrue();
        assertThat(policy.evaluate(simple(FeeType.RECURRING_FEE, IssueReason.ADMISSION)).allowed()).isFalse();
        assertThat(policy.evaluate(simple(FeeType.RECURRING_FEE, IssueReason.COURSE_ADMISSION)).allowed()).isTrue();
        assertThat(policy.evaluate(simple(FeeType.ENROLLMENT_FEE, IssueReason.COURSE_ADMISSION)).allowed()).isFalse();
    }

    @Test
    void alreadyIssuedIsRejected() {
        NotAlreadyIssuedPolicy policy = new NotAlreadyIssuedPolicy();

        assertThat(policy.evaluate(context(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB, TODAY, null, null, false, ars(1), 7L, null)).code()).isEqualTo("ALREADY_ISSUED");
        assertThat(policy.evaluate(simple(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB)).allowed()).isTrue();
    }

    @Test
    void currencyMustMatchTheOffering() {
        OfferingCurrencyPolicy policy = new OfferingCurrencyPolicy();

        assertThat(policy.evaluate(simple(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB)).allowed()).isTrue();
        assertThat(policy.evaluate(context(FeeType.RECURRING_FEE, IssueReason.MONTHLY_JOB, TODAY, null, null, false,
                Money.of(BigDecimal.TEN, Currency.USD), null, null)).code()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void entryDiscountAppliesOnlyToEntryFeesThatDoNotComeFromAdmission() {
        EntryFeeDiscountPolicy policy = new EntryFeeDiscountPolicy();
        Percentage fifty = Percentage.ofPoints(BigDecimal.valueOf(50));

        assertThat(policy.evaluate(context(FeeType.ENROLLMENT_FEE, IssueReason.MONTHLY_JOB, TODAY, 1L, 1, true, ars(1), null, fifty)).allowed()).isTrue();
        assertThat(policy.evaluate(context(FeeType.ENROLLMENT_FEE, IssueReason.ADMISSION, TODAY, 1L, 1, true, ars(1), null, fifty)).allowed()).isFalse();
        assertThat(policy.evaluate(context(FeeType.ENROLLMENT_FEE, IssueReason.MONTHLY_JOB, TODAY, 1L, 1, false, ars(1), null, fifty)).allowed()).isFalse();
        assertThat(policy.evaluate(context(FeeType.ENROLLMENT_FEE, IssueReason.MONTHLY_JOB, TODAY, 1L, 1, true, ars(1), null, Percentage.ofPoints(BigDecimal.ZERO))).allowed()).isFalse();
        assertThat(policy.evaluate(context(FeeType.ENROLLMENT_FEE, IssueReason.MONTHLY_JOB, TODAY, 1L, 1, true, ars(1), null, null)).allowed()).isFalse();
    }

    @Test
    void promotionBenefitCapIsExhaustedAtTheScopeCount() {
        PromotionBenefitCapPolicy policy = new PromotionBenefitCapPolicy();

        assertThat(policy.evaluate(new PromotionBenefitCapPolicy.Usage(null, 100)).allowed()).isTrue();
        assertThat(policy.evaluate(new PromotionBenefitCapPolicy.Usage(3, 2)).allowed()).isTrue();
        assertThat(policy.evaluate(new PromotionBenefitCapPolicy.Usage(3, 3)).code()).isEqualTo("PROMOTION_BENEFIT_EXHAUSTED");
    }
}
