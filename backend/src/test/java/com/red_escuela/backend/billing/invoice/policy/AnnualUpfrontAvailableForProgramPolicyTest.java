package com.red_escuela.backend.billing.invoice.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualUpfrontAvailableForProgramPolicyTest {
    private final AnnualUpfrontAvailableForProgramPolicy policy = new AnnualUpfrontAvailableForProgramPolicy();

    @Test
    @Tag("D-131")
    @Tag("D-191")
    void annualUpfrontOfAProgramIsRejectedWithItsViolationCode() {
        var verdict = policy.evaluateRule(new PaymentPlanChoice(true, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("ANNUAL_UPFRONT_NOT_AVAILABLE");
        assertThat(verdict.policy()).isEqualTo("AnnualUpfrontAvailableForProgramPolicy");
    }

    @Test
    @Tag("D-131")
    @Tag("D-191")
    void monthlyPlanOfAProgramIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(false, true)).allowed()).isTrue();
    }

    @Test
    @Tag("D-039")
    @Tag("D-191")
    void annualUpfrontOfACourseIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(true, false)).allowed()).isTrue();
    }

    @Test
    @Tag("D-191")
    void monthlyPlanOfACourseIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(false, false)).allowed()).isTrue();
    }
}
