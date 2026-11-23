package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumEntryView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.CurriculumProgressRow;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurriculumProgressReaderTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final CurriculumProgressReader reader = new CurriculumProgressReader(
            enrollmentRepository, sectionRepository, approvalRepository, curriculumFinder);
    private final Program program = mock(Program.class);
    private final ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
    private final List<CurriculumProgressRow> rows = new ArrayList<>();

    private List<ProgramCurriculumEntryView> plan = List.of();
    private Map<Long, List<PrerequisiteView>> prerequisites = Map.of();

    @BeforeEach
    void setUp() {
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(program.getName()).thenReturn("Tecnicatura");
        when(program.getTotalCreditsRequired()).thenReturn(12);
        when(curriculumFinder.findProgramCorequisites(PROGRAM_ID)).thenReturn(Map.of());
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of());
    }

    private CurriculumProgressView read() {
        when(curriculumFinder.findActiveCurriculumEntries(PROGRAM_ID)).thenReturn(plan);
        when(curriculumFinder.findProgramPrerequisites(PROGRAM_ID)).thenReturn(prerequisites);
        when(sectionRepository.findCurriculumProgressRows(ENROLLMENT_ID)).thenReturn(rows);
        return reader.readProgress(ENROLLMENT_ID);
    }

    private static ProgramCurriculumEntryView entry(Long id, int credits) {
        return new ProgramCurriculumEntryView(id, id, "S" + id, "Materia " + id, credits, 1, null,
                ProgramSubjectCategory.MANDATORY, null);
    }

    private static PrerequisiteView requires(Long subjectId, RequirementType type) {
        return new PrerequisiteView(subjectId, "Materia " + subjectId, type, "S" + subjectId);
    }

    private void mark(Long subjectId, EnrollmentStatus status, Grade score) {
        mark(null, subjectId, status, score);
    }

    private void mark(Long enrollmentId, Long subjectId, EnrollmentStatus status, Grade score) {
        CurriculumProgressRow row = mock(CurriculumProgressRow.class);
        when(row.getEnrollmentId()).thenReturn(enrollmentId);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getStatus()).thenReturn(status);
        when(row.getFinalScore()).thenReturn(score);
        rows.add(row);
    }

    private CurriculumProgressView.SubjectView subjectOf(CurriculumProgressView view, Long id) {
        return view.subjects().stream().filter(subject -> subject.subjectId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void twoApprovedOfFourSubjectsOfThreeCreditsIsFiftyPercent() {
        plan = List.of(entry(1L, 3), entry(2L, 3), entry(3L, 3), entry(4L, 3));
        mark(1L, EnrollmentStatus.APPROVED, Grade.of(BigDecimal.valueOf(8)));
        mark(2L, EnrollmentStatus.APPROVED, Grade.of(BigDecimal.valueOf(7)));

        CurriculumProgressView.Summary summary = read().summary();

        assertThat(summary.accumulatedCredits()).isEqualTo(6);
        assertThat(summary.requiredCredits()).isEqualTo(12);
        assertThat(summary.remainingCredits()).isEqualTo(6);
        assertThat(summary.completionPercent().points()).isEqualByComparingTo("50.00");
        assertThat(summary.mandatoryTotal()).isEqualTo(4);
        assertThat(summary.mandatoryApproved()).isEqualTo(2);
    }

    @Test
    void completionIsCappedAtOneHundredPercentWhenCreditsExceedTheRequirement() {
        plan = List.of(entry(1L, 10), entry(2L, 10));
        mark(1L, EnrollmentStatus.APPROVED, null);
        mark(2L, EnrollmentStatus.APPROVED, null);

        CurriculumProgressView.Summary summary = read().summary();

        assertThat(summary.completionPercent().points()).isEqualByComparingTo("100.00");
        assertThat(summary.remainingCredits()).isZero();
    }

    @Test
    void planWithoutRequiredCreditsHasNoPercentAndWarns() {
        when(program.getTotalCreditsRequired()).thenReturn(0);
        plan = List.of(entry(1L, 3));

        CurriculumProgressView view = read();

        assertThat(view.summary().completionPercent()).isNull();
        assertThat(view.warnings()).extracting(CurriculumProgressView.Warning::code).containsExactly("PLAN_WITHOUT_CREDITS");
    }

    @Test
    void emptyPlanHasNoSubjectsAndNoPercentOfProgress() {
        when(program.getTotalCreditsRequired()).thenReturn(0);

        CurriculumProgressView view = read();

        assertThat(view.subjects()).isEmpty();
        assertThat(view.summary().accumulatedCredits()).isZero();
    }

    @Test
    void studentWithNothingApprovedHasZeroCreditsAndSubjectsWithoutPrerequisitesAvailable() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));

        CurriculumProgressView view = read();

        assertThat(view.summary().accumulatedCredits()).isZero();
        assertThat(view.summary().completionPercent().points()).isEqualByComparingTo("0.00");
        assertThat(subjectOf(view, 1L).state()).isEqualTo(CurriculumSubjectState.AVAILABLE);
        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.BLOCKED);
        assertThat(view.summary().counts().available()).isEqualTo(1);
        assertThat(view.summary().counts().blocked()).isEqualTo(1);
    }

    @Test
    void blockedSubjectReportsTheMissingPrerequisiteAndItsStanding() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        mark(1L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView.SubjectView blocked = subjectOf(read(), 2L);

        assertThat(blocked.state()).isEqualTo(CurriculumSubjectState.BLOCKED);
        assertThat(blocked.blockedBy()).singleElement().satisfies(blocking -> {
            assertThat(blocking.subjectId()).isEqualTo(1L);
            assertThat(blocking.standing()).isEqualTo(PrerequisiteStanding.REGULAR);
            assertThat(blocking.requirementType()).isEqualTo(RequirementType.APPROVED);
        });
    }

    @Test
    void regularPrerequisiteUnlocksWhenPassedCourseworkSuffices() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.PASSED_COURSEWORK)));
        mark(1L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView.SubjectView unlocked = subjectOf(read(), 2L);

        assertThat(unlocked.state()).isEqualTo(CurriculumSubjectState.AVAILABLE);
        assertThat(unlocked.blockedBy()).isEmpty();
    }

    @Test
    void approvedPrerequisiteWaiverUnlocksButAddsNoCredits() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of(2L));

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.AVAILABLE);
        assertThat(view.summary().accumulatedCredits()).isZero();
    }

    @Test
    void waiverOnAnAlreadyRegularSubjectKeepsRegularPendingFinal() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of(2L));
        mark(2L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView.SubjectView regular = subjectOf(read(), 2L);

        assertThat(regular.state()).isEqualTo(CurriculumSubjectState.REGULAR_PENDING_FINAL);
        assertThat(regular.blockedBy()).isEmpty();
    }

    @Test
    void waiverOnAFailedSubjectKeepsFailedWithoutBlockers() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of(2L));
        mark(2L, EnrollmentStatus.FAILED, null);

        CurriculumProgressView.SubjectView failed = subjectOf(read(), 2L);

        assertThat(failed.state()).isEqualTo(CurriculumSubjectState.FAILED);
        assertThat(failed.blockedBy()).isEmpty();
    }

    @Test
    void waiverIsLookedUpOnlyForTheRequestedEnrollment() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        when(approvalRepository.findSubjectIdsWithRequest(eq(777L), any(), any())).thenReturn(List.of(2L));

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.BLOCKED);
        verify(approvalRepository).findSubjectIdsWithRequest(eq(ENROLLMENT_ID), any(), any());
    }

    @Test
    void failedSubjectKeepsFailedStateAndStillReportsUnmetPrerequisites() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.APPROVED)));
        mark(2L, EnrollmentStatus.FAILED, null);

        CurriculumProgressView.SubjectView failed = subjectOf(read(), 2L);

        assertThat(failed.state()).isEqualTo(CurriculumSubjectState.FAILED);
        assertThat(failed.blockedBy()).hasSize(1);
        assertThat(failed.attempts()).isEqualTo(1);
    }

    @Test
    void approvedBeatsOtherAttemptsAndExposesTheApprovedScoreOnce() {
        plan = List.of(entry(1L, 3));
        mark(1L, EnrollmentStatus.FAILED, Grade.of(BigDecimal.valueOf(3)));
        mark(1L, EnrollmentStatus.APPROVED, Grade.of(BigDecimal.valueOf(8)));

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 1L).state()).isEqualTo(CurriculumSubjectState.APPROVED);
        assertThat(subjectOf(view, 1L).finalScore().value()).isEqualByComparingTo(new BigDecimal("8"));
        assertThat(subjectOf(view, 1L).attempts()).isEqualTo(2);
        assertThat(view.summary().accumulatedCredits()).isEqualTo(3);
    }

    @Test
    void inProgressAndRegularSubjectsDoNotAddCredits() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        mark(1L, EnrollmentStatus.IN_PROGRESS, null);
        mark(2L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 1L).state()).isEqualTo(CurriculumSubjectState.IN_PROGRESS);
        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.REGULAR_PENDING_FINAL);
        assertThat(view.summary().accumulatedCredits()).isZero();
    }

    @Test
    void approvedSubjectOutsideTheActivePlanAddsNoCreditsAndWarns() {
        plan = List.of(entry(1L, 3));
        mark(99L, EnrollmentStatus.APPROVED, null);

        CurriculumProgressView view = read();

        assertThat(view.summary().accumulatedCredits()).isZero();
        assertThat(view.warnings()).extracting(CurriculumProgressView.Warning::code)
                .containsExactly("PLAN_CHANGED_AFTER_ENROLLMENT");
    }

    @Test
    void prerequisitesListsMetAndUnmetOnesWhileBlockedByKeepsOnlyUnmet() {
        plan = List.of(entry(1L, 3), entry(2L, 3), entry(3L, 3));
        prerequisites = Map.of(3L, List.of(requires(1L, RequirementType.APPROVED), requires(2L, RequirementType.APPROVED)));
        mark(1L, EnrollmentStatus.APPROVED, null);

        CurriculumProgressView.SubjectView blocked = subjectOf(read(), 3L);

        assertThat(blocked.prerequisites()).extracting(CurriculumProgressView.BlockingPrerequisite::subjectId, CurriculumProgressView.BlockingPrerequisite::standing)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, PrerequisiteStanding.APPROVED),
                        org.assertj.core.groups.Tuple.tuple(2L, PrerequisiteStanding.NOT_TAKEN));
        assertThat(blocked.blockedBy()).extracting(CurriculumProgressView.BlockingPrerequisite::subjectId).containsExactly(2L);
    }

    @Test
    void prerequisitesAreListedEvenWhenTheSubjectIsAvailable() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(requires(1L, RequirementType.PASSED_COURSEWORK)));
        mark(1L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView.SubjectView available = subjectOf(read(), 2L);

        assertThat(available.blockedBy()).isEmpty();
        assertThat(available.prerequisites()).singleElement().satisfies(item -> {
            assertThat(item.standing()).isEqualTo(PrerequisiteStanding.REGULAR);
            assertThat(item.requirementType()).isEqualTo(RequirementType.PASSED_COURSEWORK);
        });
    }

    @Test
    void waivedSubjectListsUnmetPrerequisitesAsWaivedAndKeepsMetOnesAsTheyAre() {
        plan = List.of(entry(1L, 3), entry(2L, 3), entry(3L, 3));
        prerequisites = Map.of(3L, List.of(requires(1L, RequirementType.APPROVED), requires(2L, RequirementType.APPROVED)));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of(3L));
        mark(1L, EnrollmentStatus.APPROVED, null);

        CurriculumProgressView.SubjectView waived = subjectOf(read(), 3L);

        assertThat(waived.blockedBy()).isEmpty();
        assertThat(waived.prerequisites()).extracting(CurriculumProgressView.BlockingPrerequisite::standing)
                .containsExactly(PrerequisiteStanding.APPROVED, PrerequisiteStanding.WAIVED);
    }

    @Test
    void subjectWithoutPrerequisitesHasEmptyNonNullPrerequisites() {
        plan = List.of(entry(1L, 3));

        assertThat(subjectOf(read(), 1L).prerequisites()).isNotNull().isEmpty();
    }

    @Test
    void currentEnrollmentIdIsTheEnrolledOrInProgressEnrollment() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        mark(701L, 1L, EnrollmentStatus.IN_PROGRESS, null);
        mark(702L, 2L, EnrollmentStatus.ENROLLED, null);

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 1L).currentEnrollmentId()).isEqualTo(701L);
        assertThat(subjectOf(view, 2L).currentEnrollmentId()).isEqualTo(702L);
    }

    @Test
    void currentEnrollmentIdIgnoresFinishedAttemptsAndIsNullWithoutOne() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        mark(801L, 1L, EnrollmentStatus.FAILED, null);
        mark(802L, 1L, EnrollmentStatus.REGULAR, null);

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 1L).currentEnrollmentId()).isNull();
        assertThat(subjectOf(view, 2L).currentEnrollmentId()).isNull();
    }

    @Test
    void currentEnrollmentIdIsReadOnlyFromTheRequestedProgramEnrollment() {
        plan = List.of(entry(1L, 3));

        read();

        verify(sectionRepository).findCurriculumProgressRows(ENROLLMENT_ID);
        verify(sectionRepository, org.mockito.Mockito.never()).findCurriculumProgressRows(org.mockito.ArgumentMatchers.longThat(id -> !ENROLLMENT_ID.equals(id)));
    }

    @Test
    void ownProgressOfAnotherStudentIsNotFound() {
        com.red_escuela.backend.identity.entity.Student owner = mock(com.red_escuela.backend.identity.entity.Student.class);
        when(owner.getId()).thenReturn(10L);
        when(enrollment.getStudent()).thenReturn(owner);

        assertThatThrownBy(() -> reader.readOwnProgress(11L, ENROLLMENT_ID)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void unknownEnrollmentIsNotFound() {
        when(enrollmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.readProgress(404L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }
}
