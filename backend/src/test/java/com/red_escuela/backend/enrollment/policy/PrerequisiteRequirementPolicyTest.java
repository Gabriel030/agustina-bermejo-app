package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

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
    void relevantStatusesAreRegularAndApproved() {
        assertThat(policy.relevantStatuses()).containsExactlyInAnyOrder(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);
    }
}
