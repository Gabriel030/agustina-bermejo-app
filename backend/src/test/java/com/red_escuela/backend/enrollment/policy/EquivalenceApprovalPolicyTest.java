package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionSubject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EquivalenceApprovalPolicyTest {

    private final CurriculumSubjectStatePolicy statePolicy = new CurriculumSubjectStatePolicy();
    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final GraduationProjectionPolicy projectionPolicy = new GraduationProjectionPolicy();

    @Test
    void equivalenceMakesTheSubjectApprovedWithoutAnyCoursework() {
        assertThat(statePolicy.stateOf(List.of(), true, false)).isEqualTo(CurriculumSubjectState.APPROVED);
    }

    @Test
    void equivalenceBeatsAnInProgressOrFailedAttempt() {
        assertThat(statePolicy.stateOf(List.of(EnrollmentStatus.FAILED), true, false)).isEqualTo(CurriculumSubjectState.APPROVED);
        assertThat(statePolicy.stateOf(List.of(EnrollmentStatus.IN_PROGRESS), true, true)).isEqualTo(CurriculumSubjectState.APPROVED);
    }

    @Test
    void withoutEquivalenceTheStateIsUnchanged() {
        assertThat(statePolicy.stateOf(List.of(EnrollmentStatus.FAILED), false, true)).isEqualTo(CurriculumSubjectState.FAILED);
        assertThat(statePolicy.stateOf(List.of(), false, false)).isEqualTo(CurriculumSubjectState.BLOCKED);
        assertThat(statePolicy.stateOf(List.of(), true, true)).isEqualTo(statePolicy.stateOf(List.of(EnrollmentStatus.APPROVED), true));
    }

    @Test
    void equivalenceStandsAsApprovedAndMeetsBothRequirementTypes() {
        PrerequisiteStanding standing = requirementPolicy.standingOf(List.of(), true);

        assertThat(standing).isEqualTo(PrerequisiteStanding.APPROVED);
        assertThat(requirementPolicy.isMet(RequirementType.APPROVED, standing)).isTrue();
        assertThat(requirementPolicy.isMet(RequirementType.PASSED_COURSEWORK, standing)).isTrue();
        assertThat(requirementPolicy.isMetForApproval(standing)).isTrue();
    }

    @Test
    void withoutEquivalenceStandingFollowsTheCourseworkOnly() {
        assertThat(requirementPolicy.standingOf(List.of(), false)).isEqualTo(PrerequisiteStanding.NOT_TAKEN);
        assertThat(requirementPolicy.standingOf(List.of(EnrollmentStatus.REGULAR), false)).isEqualTo(PrerequisiteStanding.REGULAR);
    }

    @Test
    void pacePerPeriodUsesOnlyCourseworkCredits() {
        List<ProjectionSubject> subjects = new ArrayList<>();
        for (int id = 1; id <= 4; id++) {
            subjects.add(subject(id, CurriculumSubjectState.APPROVED));
        }
        for (int id = 5; id <= 12; id++) {
            subjects.add(subject(id, CurriculumSubjectState.AVAILABLE));
        }

        GraduationProjection inflated = projectionPolicy.project(input(subjects, 12, 12));
        GraduationProjection coursework = projectionPolicy.project(input(subjects, 12, 6));

        assertThat(inflated.basis().creditsPerPeriod()).isEqualByComparingTo("6.00");
        assertThat(coursework.basis().creditsPerPeriod()).isEqualByComparingTo("3.00");
        assertThat(coursework.basis().creditsPerPeriodSource()).isEqualTo(CreditsPerPeriodSource.STUDENT_HISTORY);
    }

    @Test
    void paceFallsBackToThePlanWhenEveryApprovedCreditComesFromEquivalences() {
        List<ProjectionSubject> subjects = new ArrayList<>();
        for (int id = 1; id <= 4; id++) {
            subjects.add(subject(id, CurriculumSubjectState.APPROVED));
        }
        for (int id = 5; id <= 12; id++) {
            subjects.add(subject(id, CurriculumSubjectState.AVAILABLE));
        }

        GraduationProjection projection = projectionPolicy.project(input(subjects, 12, 0));

        assertThat(projection.basis().creditsPerPeriodSource()).isEqualTo(CreditsPerPeriodSource.PLAN_THEORETICAL);
    }

    private GraduationProjectionInput input(List<ProjectionSubject> subjects, int accumulated, int courseworkCredits) {
        return new GraduationProjectionInput(1L, ProgramEnrollmentStatus.ENROLLED, 5, 2, LocalDate.of(2026, 3, 10),
                accumulated, 36, 36 - accumulated, 12, 4, 4, false, false, subjects, List.of(), courseworkCredits);
    }

    private ProjectionSubject subject(long id, CurriculumSubjectState state) {
        return new ProjectionSubject(id, 3, 1, null, null, state, List.of());
    }
}
