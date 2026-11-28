package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrerequisiteRequirementPolicyTest {

    private final PrerequisiteRequirementPolicy policy = new PrerequisiteRequirementPolicy();

    @Test
    void standingPrefersApprovedOverRegular() {
        assertThat(policy.standingOf(List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED)))
                .isEqualTo(PrerequisiteStanding.APPROVED);
    }

    @Test
    void standingIsRegularWhenOnlyRegularExists() {
        assertThat(policy.standingOf(Set.of(EnrollmentStatus.REGULAR))).isEqualTo(PrerequisiteStanding.REGULAR);
    }

    @Test
    void standingIsNotTakenWhenNothingRelevantExists() {
        assertThat(policy.standingOf(List.of())).isEqualTo(PrerequisiteStanding.NOT_TAKEN);
    }

    @Test
    void approvedRequirementIsMetOnlyByApprovedOrWaived() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED)).isTrue();
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.WAIVED)).isTrue();
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.REGULAR)).isFalse();
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.NOT_TAKEN)).isFalse();
    }

    @Test
    void passedCourseworkRequirementIsMetByRegularToo() {
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.REGULAR)).isTrue();
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.APPROVED)).isTrue();
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.WAIVED)).isTrue();
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.NOT_TAKEN)).isFalse();
    }

    @Test
    void relevantStatusesAreRegularApprovalPendingAndApproved() {
        assertThat(policy.relevantStatuses()).containsExactlyInAnyOrder(
                EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVAL_PENDING, EnrollmentStatus.APPROVED);
    }

    @Test
    void approvalPendingCountsAsRegularButNeverAsApproved() {
        PrerequisiteStanding standing = policy.standingOf(Set.of(EnrollmentStatus.APPROVAL_PENDING));

        assertThat(standing).isEqualTo(PrerequisiteStanding.REGULAR);
        assertThat(policy.isMet(RequirementType.APPROVED, standing)).isFalse();
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, standing)).isTrue();
    }

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    @Test
    void scoreEqualToMinimumIsMet() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED, grade("6"), grade("6"))).isTrue();
    }

    @Test
    void scoreOneCentBelowMinimumIsNotMet() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED, grade("6"), grade("5.99"))).isFalse();
    }

    @Test
    void scoreAboveMinimumIsMet() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED, grade("6"), grade("10"))).isTrue();
    }

    @Test
    void missingMinimumIsAlwaysMet() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED, null, grade("1"))).isTrue();
    }

    @Test
    void missingScoreWithMinimumIsMet() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.APPROVED, grade("6"), null)).isTrue();
    }

    @Test
    void waivedIsMetEvenWithLowScore() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.WAIVED, grade("6"), grade("2"))).isTrue();
    }

    @Test
    void passedCourseworkWithRegularIgnoresMinimum() {
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.REGULAR, grade("6"), null)).isTrue();
    }

    @Test
    void passedCourseworkWithApprovedIgnoresMinimum() {
        assertThat(policy.isMet(RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.APPROVED, grade("6"), grade("2"))).isTrue();
    }

    @Test
    void notTakenIsNotMetRegardlessOfMinimum() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.NOT_TAKEN, null, null)).isFalse();
    }

    @Test
    void regularIsNotMetForApprovedRequirementEvenWithoutMinimum() {
        assertThat(policy.isMet(RequirementType.APPROVED, PrerequisiteStanding.REGULAR, null, null)).isFalse();
    }
}
