package com.red_escuela.backend.billing.annualupfront.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-168")
class AnnualUpfrontPlanCannotBeChangedPolicyTest {
    private final AnnualUpfrontPlanCannotBeChangedPolicy policy = new AnnualUpfrontPlanCannotBeChangedPolicy();

    @Test
    void aConfirmedAnnualUpfrontBlocksTheChangeWithItsCode() {
        var verdict = policy.evaluateRule(new AnnualUpfrontPlanChange(true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("ANNUAL_UPFRONT_PLAN_CANNOT_BE_CHANGED");
    }

    @Test
    void withoutAConfirmedAnnualUpfrontTheChangeIsAllowed() {
        assertThat(policy.evaluateRule(new AnnualUpfrontPlanChange(false)).allowed()).isTrue();
    }
}
