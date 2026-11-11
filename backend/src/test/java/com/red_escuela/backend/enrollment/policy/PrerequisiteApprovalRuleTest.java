package com.red_escuela.backend.enrollment.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrerequisiteApprovalRuleTest {

    private final PrerequisiteRequirementPolicy policy = new PrerequisiteRequirementPolicy();

    @Test
    void onlyApprovedOrWaivedStandingsMeetTheApprovalRule() {
        assertThat(policy.isMetForApproval(PrerequisiteStanding.APPROVED)).isTrue();
        assertThat(policy.isMetForApproval(PrerequisiteStanding.WAIVED)).isTrue();
        assertThat(policy.isMetForApproval(PrerequisiteStanding.REGULAR)).isFalse();
        assertThat(policy.isMetForApproval(PrerequisiteStanding.NOT_TAKEN)).isFalse();
    }
}
