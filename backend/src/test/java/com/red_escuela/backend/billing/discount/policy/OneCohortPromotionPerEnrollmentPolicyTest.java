package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-386")
class OneCohortPromotionPerEnrollmentPolicyTest {
    private final OneCohortPromotionPerEnrollmentPolicy policy = new OneCohortPromotionPerEnrollmentPolicy();

    @Test
    void anEnrollmentWithoutACohortPromotionMayAcquireOne() {
        assertThat(policy.evaluateRule(new CohortPromotionAcquisitionCheck(null)).allowed()).isTrue();
    }

    @Test
    void anEnrollmentThatAlreadyHasACohortPromotionIsRejectedNamingTheExistingOne() {
        PolicyVerdict verdict = policy.evaluateRule(new CohortPromotionAcquisitionCheck(41L));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("COHORT_PROMOTION_ALREADY_ACQUIRED");
        assertThat(verdict.reason()).contains("41");
        assertThat(policy.ruleName()).isEqualTo("OneCohortPromotionPerEnrollmentPolicy");
    }
}
