package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionElectiveGroup;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionSubject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationProjectionPolicyTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 10);

    private final GraduationProjectionPolicy policy = new GraduationProjectionPolicy();

    @Test
    void paceFromHistoryDrivesPeriodsWhenStudentHasEnoughHistory() {
        List<ProjectionSubject> subjects = new ArrayList<>();
        subjects.addAll(subjectsOf(1, 18, CurriculumSubjectState.APPROVED));
        subjects.addAll(subjectsOf(19, 22, CurriculumSubjectState.AVAILABLE));

        GraduationProjection view = project(subjects, 120, List.of(), 5, 6, MARCH);

        assertThat(view.status()).isEqualTo(ProjectionStatus.PROJECTED);
        assertThat(view.estimatedPeriods()).isEqualTo(8);
        assertThat(view.range().minPeriods()).isEqualTo(6);
        assertThat(view.range().maxPeriods()).isEqualTo(8);
        assertThat(view.confidence()).isEqualTo(ProjectionConfidence.HIGH);
        assertThat(view.method()).isEqualTo(ProjectionMethod.HISTORICAL_PACE_WITH_PREREQUISITES);
        assertThat(view.basis().creditsPerPeriod()).isEqualByComparingTo("9.00");
        assertThat(view.basis().remainingCredits()).isEqualTo(66);
        assertThat(view.estimatedGraduationYear()).isEqualTo(2029);
    }

    @Test
    void planPaceIsUsedWhenFewerThanTwoClosedPeriods() {
        List<ProjectionSubject> subjects = new ArrayList<>();
        subjects.addAll(subjectsOf(1, 18, CurriculumSubjectState.APPROVED));
        subjects.addAll(subjectsOf(19, 22, CurriculumSubjectState.AVAILABLE));

        GraduationProjection view = project(subjects, 120, List.of(), 5, 1, MARCH);

        assertThat(view.estimatedPeriods()).isEqualTo(6);
        assertThat(view.confidence()).isEqualTo(ProjectionConfidence.LOW);
        assertThat(view.estimatedGraduationYear()).isNull();
        assertThat(view.basis().creditsPerPeriodSource()).isEqualTo(CreditsPerPeriodSource.PLAN_THEORETICAL);
        assertThat(view.warnings()).extracting(GraduationProjection.Message::code)
                .contains("PACE_BASED_ON_FEW_PERIODS");
    }

    @Test
    void criticalPathIsAFloorForPeriods() {
        List<ProjectionSubject> subjects = new ArrayList<>(subjectsOf(1, 6, CurriculumSubjectState.APPROVED));
        subjects.add(subject(7, 3, CurriculumSubjectState.AVAILABLE, null, null));
        subjects.add(subject(8, 3, CurriculumSubjectState.BLOCKED, null, null, 7L));
        subjects.add(subject(9, 3, CurriculumSubjectState.BLOCKED, null, null, 8L));

        GraduationProjection view = project(subjects, 27, List.of(), 5, 2, MARCH);

        assertThat(view.basis().creditsPerPeriod()).isEqualByComparingTo("9.00");
        assertThat(view.basis().criticalPathPeriods()).isEqualTo(3);
        assertThat(view.estimatedPeriods()).isEqualTo(3);
        assertThat(view.range().minPeriods()).isEqualTo(3);
    }

    @Test
    void annualSubjectOccupiesTwoPeriods() {
        List<ProjectionSubject> subjects = List.of(subject(1, 6, CurriculumSubjectState.AVAILABLE, CurricularTerm.ANNUAL, null));

        assertThat(project(subjects, 6, List.of(), 3, 0, MARCH).estimatedPeriods()).isEqualTo(2);
        assertThat(project(subjects, 6, List.of(), 3, 0, AUGUST).estimatedPeriods()).isEqualTo(3);
    }

    @Test
    void subjectIsOnlyStartedInTheTermWhereItIsOffered() {
        List<ProjectionSubject> subjects = List.of(subject(1, 3, CurriculumSubjectState.AVAILABLE, CurricularTerm.SECOND, null));

        assertThat(project(subjects, 3, List.of(), 3, 0, MARCH).estimatedPeriods()).isEqualTo(2);
        assertThat(project(subjects, 3, List.of(), 3, 0, AUGUST).estimatedPeriods()).isEqualTo(1);
    }

    @Test
    void studentWithNoApprovedSubjectsAndNoPlanDurationIsNotProjectable() {
        List<ProjectionSubject> subjects = subjectsOf(1, 4, CurriculumSubjectState.AVAILABLE);

        GraduationProjection view = project(subjects, 12, List.of(), null, 0, MARCH);

        assertThat(view.status()).isEqualTo(ProjectionStatus.NOT_PROJECTABLE);
        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.PLAN_WITHOUT_DURATION);
        assertThat(view.estimatedPeriods()).isNull();
    }

    @Test
    void unreachableCreditsAreReported() {
        List<ProjectionSubject> subjects = subjectsOf(1, 20, CurriculumSubjectState.AVAILABLE);
        subjects = new ArrayList<>(subjects.stream().map(subject -> withCredits(subject, 5)).toList());

        GraduationProjection view = project(subjects, 120, List.of(), 5, 0, MARCH);

        assertThat(view.status()).isEqualTo(ProjectionStatus.NOT_PROJECTABLE);
        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.CREDITS_UNREACHABLE);
    }

    @Test
    void prerequisiteCycleIsReported() {
        List<ProjectionSubject> subjects = List.of(
                subject(1, 3, CurriculumSubjectState.BLOCKED, null, null, 2L),
                subject(2, 3, CurriculumSubjectState.BLOCKED, null, null, 1L));

        GraduationProjection view = project(subjects, 6, List.of(), 5, 0, MARCH);

        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.PREREQUISITE_CYCLE);
    }

    @Test
    void prerequisiteOutsideTheActivePlanIsReportedAsInactiveRequiredSubject() {
        List<ProjectionSubject> subjects = List.of(subject(1, 3, CurriculumSubjectState.BLOCKED, null, null, 99L));

        GraduationProjection view = project(subjects, 3, List.of(), 5, 0, MARCH);

        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.REQUIRED_SUBJECT_INACTIVE);
    }

    @Test
    void requirementsMetShortCircuitsToZeroPeriods() {
        GraduationProjection view = project(subjectsOf(1, 4, CurriculumSubjectState.APPROVED), 12, List.of(), 5, 6, MARCH);

        assertThat(view.status()).isEqualTo(ProjectionStatus.REQUIREMENTS_MET);
        assertThat(view.estimatedPeriods()).isZero();
    }

    @Test
    void withdrawnEnrollmentIsNotProjectable() {
        GraduationProjection view = project(subjectsOf(1, 4, CurriculumSubjectState.AVAILABLE), 12, List.of(), 5, 6,
                MARCH, ProgramEnrollmentStatus.WITHDRAWN);

        assertThat(view.status()).isEqualTo(ProjectionStatus.NOT_PROJECTABLE);
        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.ENROLLMENT_WITHDRAWN);
    }

    @Test
    void pausedEnrollmentIsNotProjectable() {
        GraduationProjection view = project(subjectsOf(1, 4, CurriculumSubjectState.AVAILABLE), 12, List.of(), 5, 6,
                MARCH, ProgramEnrollmentStatus.PAUSED);

        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.ENROLLMENT_PAUSED);
    }

    @Test
    void emptyPlanAndPlanWithoutCreditsAreReported() {
        assertThat(project(List.of(), 12, List.of(), 5, 6, MARCH).reasons())
                .containsExactly(ProjectionBlockReason.NO_PLAN_SUBJECTS);
        assertThat(project(subjectsOf(1, 2, CurriculumSubjectState.AVAILABLE), 0, List.of(), 5, 6, MARCH).reasons())
                .containsExactly(ProjectionBlockReason.PLAN_WITHOUT_CREDITS);
    }

    @Test
    void regularFinalsDoNotConsumeCreditCapacity() {
        List<ProjectionSubject> subjects = List.of(
                subject(1, 3, CurriculumSubjectState.REGULAR_PENDING_FINAL, null, null),
                subject(2, 3, CurriculumSubjectState.AVAILABLE, null, null));

        assertThat(project(subjects, 6, List.of(), 3, 0, MARCH).estimatedPeriods()).isEqualTo(1);
    }

    @Test
    void subjectsInProgressConsumeCreditCapacityOfTheCurrentPeriod() {
        List<ProjectionSubject> subjects = List.of(
                subject(1, 3, CurriculumSubjectState.IN_PROGRESS, null, null),
                subject(2, 3, CurriculumSubjectState.AVAILABLE, null, null));

        assertThat(project(subjects, 6, List.of(), 1, 0, MARCH).estimatedPeriods()).isEqualTo(2);
    }

    @Test
    void electiveGroupMinimumAddsRemainingElectives() {
        List<ProjectionSubject> subjects = List.of(
                subject(1, 3, CurriculumSubjectState.APPROVED, null, null),
                subject(2, 3, CurriculumSubjectState.AVAILABLE, null, 7L),
                subject(3, 3, CurriculumSubjectState.AVAILABLE, null, 7L),
                subject(4, 3, CurriculumSubjectState.AVAILABLE, null, 7L));
        List<ProjectionElectiveGroup> groups = List.of(new ProjectionElectiveGroup(7L, 2, 3, false));

        GraduationProjection view = project(subjects, 9, groups, 3, 0, MARCH);

        assertThat(view.status()).isEqualTo(ProjectionStatus.PROJECTED);
        assertThat(view.basis().remainingElectiveSubjects()).isEqualTo(2);
        assertThat(view.basis().remainingCredits()).isEqualTo(6);
    }

    @Test
    void electivesBeyondTheGroupMaximumDoNotCoverMissingCredits() {
        List<ProjectionSubject> subjects = List.of(
                subject(1, 3, CurriculumSubjectState.APPROVED, null, null),
                subject(2, 3, CurriculumSubjectState.AVAILABLE, null, 7L),
                subject(3, 3, CurriculumSubjectState.AVAILABLE, null, 7L),
                subject(4, 3, CurriculumSubjectState.AVAILABLE, null, 7L));
        List<ProjectionElectiveGroup> groups = List.of(new ProjectionElectiveGroup(7L, 1, 1, false));

        GraduationProjection view = project(subjects, 12, groups, 3, 0, MARCH);

        assertThat(view.reasons()).containsExactly(ProjectionBlockReason.CREDITS_UNREACHABLE);
    }

    @Test
    void confidenceBordersFollowClosedPeriodsAndApprovedSubjects() {
        assertThat(policy.confidenceOf(1, 2)).isEqualTo(ProjectionConfidence.LOW);
        assertThat(policy.confidenceOf(2, 2)).isEqualTo(ProjectionConfidence.MEDIUM);
        assertThat(policy.confidenceOf(3, 2)).isEqualTo(ProjectionConfidence.MEDIUM);
        assertThat(policy.confidenceOf(4, 2)).isEqualTo(ProjectionConfidence.LOW);
        assertThat(policy.confidenceOf(1, 3)).isEqualTo(ProjectionConfidence.MEDIUM);
        assertThat(policy.confidenceOf(4, 7)).isEqualTo(ProjectionConfidence.MEDIUM);
        assertThat(policy.confidenceOf(4, 8)).isEqualTo(ProjectionConfidence.HIGH);
        assertThat(policy.confidenceOf(3, 8)).isEqualTo(ProjectionConfidence.MEDIUM);
    }

    @Test
    void closedPeriodsCountCalendarSemestersSinceTheFirstCourse() {
        assertThat(policy.closedPeriodsBetween(null, MARCH)).isZero();
        assertThat(policy.closedPeriodsBetween(LocalDate.of(2026, 2, 1), MARCH)).isZero();
        assertThat(policy.closedPeriodsBetween(LocalDate.of(2025, 8, 1), MARCH)).isEqualTo(1);
        assertThat(policy.closedPeriodsBetween(LocalDate.of(2023, 3, 1), AUGUST)).isEqualTo(7);
    }

    @Test
    void graduationYearIsTheYearWhereTheLastPeriodEnds() {
        assertThat(policy.graduationYearOf(MARCH, 1)).isEqualTo(2026);
        assertThat(policy.graduationYearOf(MARCH, 2)).isEqualTo(2026);
        assertThat(policy.graduationYearOf(MARCH, 3)).isEqualTo(2027);
        assertThat(policy.graduationYearOf(AUGUST, 2)).isEqualTo(2027);
    }

    private GraduationProjection project(List<ProjectionSubject> subjects, int requiredCredits,
            List<ProjectionElectiveGroup> groups, Integer totalYears, int closedPeriods, LocalDate today) {
        return project(subjects, requiredCredits, groups, totalYears, closedPeriods, today, ProgramEnrollmentStatus.ENROLLED);
    }

    private GraduationProjection project(List<ProjectionSubject> subjects, int requiredCredits,
            List<ProjectionElectiveGroup> groups, Integer totalYears, int closedPeriods, LocalDate today,
            ProgramEnrollmentStatus status) {
        int accumulated = subjects.stream().filter(subject -> subject.state() == CurriculumSubjectState.APPROVED)
                .mapToInt(ProjectionSubject::credits).sum();
        int approved = (int) subjects.stream().filter(subject -> subject.state() == CurriculumSubjectState.APPROVED).count();
        int mandatoryTotal = (int) subjects.stream().filter(subject -> subject.electiveGroupId() == null).count();
        int mandatoryApproved = (int) subjects.stream().filter(subject -> subject.electiveGroupId() == null
                && subject.state() == CurriculumSubjectState.APPROVED).count();
        return policy.project(new GraduationProjectionInput(1L, status, totalYears, closedPeriods, today, accumulated,
                requiredCredits, Math.max(0, requiredCredits - accumulated), mandatoryTotal, mandatoryApproved, approved,
                false, false, subjects, groups));
    }

    private List<ProjectionSubject> subjectsOf(int firstId, int count, CurriculumSubjectState state) {
        List<ProjectionSubject> subjects = new ArrayList<>();
        for (int id = firstId; id < firstId + count; id++) {
            subjects.add(subject(id, 3, state, null, null));
        }
        return subjects;
    }

    private ProjectionSubject withCredits(ProjectionSubject subject, int credits) {
        return new ProjectionSubject(subject.subjectId(), credits, subject.curricularYear(), subject.curricularTerm(),
                subject.electiveGroupId(), subject.state(), subject.unmetPrerequisiteSubjectIds());
    }

    private ProjectionSubject subject(long id, int credits, CurriculumSubjectState state, CurricularTerm term, Long groupId,
            Long... unmetPrerequisiteSubjectIds) {
        return new ProjectionSubject(id, credits, 1, term, groupId, state, List.of(unmetPrerequisiteSubjectIds));
    }
}
