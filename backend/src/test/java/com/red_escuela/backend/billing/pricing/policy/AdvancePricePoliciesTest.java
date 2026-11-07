package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.PricingPolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-532")
class AdvancePricePoliciesTest {
    private static final LocalDate EMISSION_START = LocalDate.of(2026, 12, 1);

    private static AdvancePriceCheck check(FeeType feeType, LocalDate effectiveFrom, Optional<LocalDate> emissionStart) {
        return new AdvancePriceCheck(feeType, 2027, effectiveFrom, emissionStart);
    }

    @Test
    void aVersionStartingAfterTheRegularEmissionIsRejectedWithItsCode() {
        PolicyVerdict denial = new AdvancePriceStartsBeforeRegularEmissionPolicy()
                .evaluateRule(check(FeeType.ENROLLMENT_FEE, EMISSION_START.plusDays(1), Optional.of(EMISSION_START)));

        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("ADVANCE_PRICE_STARTS_AFTER_REGULAR_EMISSION");
        assertThat(AdvancePriceStartsBeforeRegularEmissionPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
    }

    @Test
    void aVersionStartingOnOrBeforeTheRegularEmissionIsAllowed() {
        AdvancePriceStartsBeforeRegularEmissionPolicy policy = new AdvancePriceStartsBeforeRegularEmissionPolicy();

        assertThat(policy.evaluateRule(check(FeeType.ENROLLMENT_FEE, EMISSION_START, Optional.of(EMISSION_START))).allowed()).isTrue();
        assertThat(policy.evaluateRule(check(FeeType.ENROLLMENT_FEE, EMISSION_START.minusMonths(2), Optional.of(EMISSION_START))).allowed()).isTrue();
    }

    @Test
    void withoutAYearConfigurationTheStartCannotBeComparedSoItIsAllowed() {
        assertThat(new AdvancePriceStartsBeforeRegularEmissionPolicy().evaluateRule(check(FeeType.ENROLLMENT_FEE, EMISSION_START.plusYears(1), Optional.empty())).allowed()).isTrue();
    }

    @Test
    void onlyTheEnrollmentFeePriceAcceptsALectiveYear() {
        LectiveYearOnlyForEnrollmentFeePolicy policy = new LectiveYearOnlyForEnrollmentFeePolicy();

        PolicyVerdict denial = policy.evaluateRule(check(FeeType.MONTHLY_INSTALLMENT, EMISSION_START, Optional.empty()));

        assertThat(denial.code()).isEqualTo("LECTIVE_YEAR_ONLY_FOR_ENROLLMENT_FEE");
        assertThat(LectiveYearOnlyForEnrollmentFeePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(policy.evaluateRule(check(FeeType.ENROLLMENT_FEE, EMISSION_START, Optional.empty())).allowed()).isTrue();
    }

    @Test
    void thePolicySetReportsBothViolationsTogether() {
        var result = new PricingPolicySetsConfiguration().advancePricePolicySet()
                .evaluateAllRules(check(FeeType.MONTHLY_INSTALLMENT, EMISSION_START.plusDays(5), Optional.of(EMISSION_START)));

        assertThat(result.violations()).extracting(violation -> violation.code())
                .containsExactly("LECTIVE_YEAR_ONLY_FOR_ENROLLMENT_FEE", "ADVANCE_PRICE_STARTS_AFTER_REGULAR_EMISSION");
    }
}
