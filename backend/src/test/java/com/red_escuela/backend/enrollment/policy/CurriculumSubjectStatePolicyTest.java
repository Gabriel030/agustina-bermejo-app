package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurriculumSubjectStatePolicyTest {

    private final CurriculumSubjectStatePolicy policy = new CurriculumSubjectStatePolicy();

    @Test
    void givenApprovalPendingOnly_whenResolvingState_thenItIsInProgressAndNeverApprovedNorPendingFinal() {
        assertThat(policy.stateOf(List.of(EnrollmentStatus.APPROVAL_PENDING), true))
                .isEqualTo(CurriculumSubjectState.IN_PROGRESS);
    }

    @Test
    void givenApprovalPendingAndApproved_whenResolvingState_thenApprovedWins() {
        assertThat(policy.stateOf(List.of(EnrollmentStatus.APPROVAL_PENDING, EnrollmentStatus.APPROVED), true))
                .isEqualTo(CurriculumSubjectState.APPROVED);
    }

    @Test
    void givenApprovalPending_whenCountingAttempts_thenItCounts() {
        assertThat(policy.countsAsAttempt(EnrollmentStatus.APPROVAL_PENDING)).isTrue();
    }
}
