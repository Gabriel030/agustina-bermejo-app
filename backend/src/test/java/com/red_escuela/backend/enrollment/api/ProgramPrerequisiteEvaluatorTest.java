package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.PrerequisiteStatusRow;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramPrerequisiteEvaluatorTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;
    private static final Long SUBJECT_ID = 30L;
    private static final Long PROGRAM_SUBJECT_ID = 300L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final SubjectEquivalenceRepository equivalenceRepository = mock(SubjectEquivalenceRepository.class);
    private final ProgramPrerequisiteEvaluator evaluator = new ProgramPrerequisiteEvaluator(
            enrollmentRepository, new SubjectApprovalLookup(sectionRepository, equivalenceRepository),
            approvalRepository, curriculumFinder);

    private final List<SubjectSectionEnrollmentRepository.PrerequisiteStatusRow> studentRows = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        when(sectionRepository.findPrerequisiteStatusRows(any(), any(), any())).thenReturn(studentRows);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getProgram()).thenReturn(program);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, SUBJECT_ID)).thenReturn(Optional.of(
                new ProgramSubjectView(PROGRAM_SUBJECT_ID, SUBJECT_ID, "Algebra II", 4, 1, null, null)));
    }

    private void prerequisites(PrerequisiteView... views) {
        when(curriculumFinder.findPrerequisites(PROGRAM_SUBJECT_ID)).thenReturn(List.of(views));
    }

    private void studentApprovedWithScore(Long prerequisiteSubjectId, String... scores) {
        Arrays.stream(scores).map(score -> {
            PrerequisiteStatusRow row = mock(PrerequisiteStatusRow.class);
            when(row.getProgramEnrollmentId()).thenReturn(ENROLLMENT_ID);
            when(row.getSubjectId()).thenReturn(prerequisiteSubjectId);
            when(row.getStatus()).thenReturn(EnrollmentStatus.APPROVED);
            when(row.getFinalScore()).thenReturn(score == null ? null : Grade.of(new BigDecimal(score)));
            return row;
        }).forEach(studentRows::add);
    }

    private void studentHasEquivalence(Long prerequisiteSubjectId, String originScore) {
        ProgramEnrollment programEnrollment = mock(ProgramEnrollment.class);
        when(programEnrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(equivalenceRepository.findByProgramEnrollmentIdsAndStatus(any(), eq(SubjectEquivalenceStatus.ACTIVE)))
                .thenReturn(List.of(SubjectEquivalence.builder().programEnrollment(programEnrollment)
                        .subjectId(prerequisiteSubjectId).originInstitution("Otra").originSubjectName("Algebra")
                        .originScore(originScore == null ? null : Grade.of(new BigDecimal(originScore))).build()));
    }

    private static PrerequisiteView prerequisiteWithMinimum(RequirementType type, String minGrade) {
        return new PrerequisiteView(10L, "Algebra I", type, "C10", Grade.of(new BigDecimal(minGrade)));
    }

    private void studentHas(Long prerequisiteSubjectId, EnrollmentStatus... statuses) {
        java.util.Arrays.stream(statuses).map(status -> {
            SubjectSectionEnrollmentRepository.PrerequisiteStatusRow row = mock(SubjectSectionEnrollmentRepository.PrerequisiteStatusRow.class);
            when(row.getProgramEnrollmentId()).thenReturn(ENROLLMENT_ID);
            when(row.getSubjectId()).thenReturn(prerequisiteSubjectId);
            when(row.getStatus()).thenReturn(status);
            return row;
        }).forEach(studentRows::add);
    }

    @Test
    void approvedRequirementWithOnlyRegularStandingIsNotMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED, "C10", null));
        studentHas(10L, EnrollmentStatus.REGULAR);

        List<PrerequisiteAssessment> result = evaluator.assess(ENROLLMENT_ID, SUBJECT_ID);

        assertThat(result).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.APPROVED, PrerequisiteStanding.REGULAR, false, null, null));
    }

    @Test
    void passedCourseworkRequirementWithRegularStandingIsMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null));
        studentHas(10L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void aPrerequisiteTheStudentNeverTookIsNotMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null));
        studentHas(10L);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.NOT_TAKEN, false, null, null));
    }

    @Test
    void anApprovedWaiverMarksEveryPrerequisiteAsWaivedWithoutQueryingTheStudentHistory() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED, "C10", null));
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.APPROVED, PrerequisiteStanding.WAIVED, true, null, null));
        verify(sectionRepository, never()).findPrerequisiteStatusRows(any(), any(), any());
    }

    @Test
    void aRegularPrerequisiteLetsTheStudentEnrollButNotApprove() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null));
        studentHas(10L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isTrue();
        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isFalse();
    }

    @Test
    void anApprovedPrerequisiteAllowsApproval() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null));
        studentHas(10L, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);

        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isTrue();
    }

    @Test
    void aWaivedPrerequisiteAllowsApprovalWithoutHistory() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED, "C10", null));
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isTrue();
    }

    @Test
    void aSubjectOutsideThePlanDoesNotBlockApproval() {
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 99L)).thenReturn(Optional.empty());

        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, 99L)).isTrue();
    }

    @Test
    void aSubjectWithoutPrerequisitesHasNoAssessments() {
        prerequisites();

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void aSubjectOutsideThePlanIsRejectedEvenWhenAWaiverExists() {
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 99L)).thenReturn(Optional.empty());
        when(approvalRepository.hasApproved(ENROLLMENT_ID, 99L, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThatThrownBy(() -> evaluator.assess(ENROLLMENT_ID, 99L)).isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void anUnknownProgramEnrollmentIsRejected() {
        assertThatThrownBy(() -> evaluator.assess(404L, SUBJECT_ID)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void allMetIsFalseWhenAnyPrerequisiteIsUnmet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED, "C10", null),
                new PrerequisiteView(11L, "Analisis I", RequirementType.APPROVED, "C11", null));
        studentHas(10L, EnrollmentStatus.APPROVED);
        studentHas(11L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isFalse();
    }

    @Test
    void allMetIsTrueWhenEveryPrerequisiteIsMetOrWaived() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED, "C10", null));
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isTrue();
    }

    @Test
    void allMetIsTrueForASubjectOutsideThePlan() {
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 99L)).thenReturn(Optional.empty());

        assertThat(evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, 99L)).isTrue();
    }

    @Test
    void unmetForApprovalListsOnlyPrerequisitesNotApprovedNorWaived() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null),
                new PrerequisiteView(11L, "Analisis I", RequirementType.PASSED_COURSEWORK, "C11", null));
        studentHas(10L, EnrollmentStatus.REGULAR);
        studentHas(11L, EnrollmentStatus.APPROVED);

        assertThat(evaluator.findUnmetForApprovalOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID))
                .containsExactly(new PrerequisiteAssessment(
                        10L, "Algebra I", RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.REGULAR, true, null, null));
    }

    @Test
    void unmetForApprovalIsEmptyWhenAWaiverIsApproved() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK, "C10", null));
        studentHas(10L, EnrollmentStatus.REGULAR);
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.findUnmetForApprovalOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void unmetForApprovalIsEmptyWhenTheSubjectIsOutsideThePlan() {
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 99L)).thenReturn(Optional.empty());

        assertThat(evaluator.findUnmetForApprovalOrEmptyOutsidePlan(ENROLLMENT_ID, 99L)).isEmpty();
    }

    @Test
    void approvedPrerequisiteBelowMinimumIsNotMetAndExposesMinimumAndScore() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "5.5");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.met()).isFalse();
            assertThat(assessment.standing()).isEqualTo(PrerequisiteStanding.APPROVED);
            assertThat(assessment.minGrade()).isEqualTo(Grade.of(new BigDecimal("6")));
            assertThat(assessment.approvedScore()).isEqualTo(Grade.of(new BigDecimal("5.5")));
        });
        assertThat(evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isFalse();
    }

    @Test
    void approvedPrerequisiteWithScoreEqualToMinimumIsMet() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "6");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void approvedPrerequisiteWithoutScoreIsMetEvenWithMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, (String) null);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void bestScoreAmongSeveralApprovedAttemptsDecides() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "4", "7", "5");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.met()).isTrue();
            assertThat(assessment.approvedScore()).isEqualTo(Grade.of(new BigDecimal("7")));
        });
    }

    @Test
    void approvedWaiverCoversMinimumGrade() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "2");
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.met()).isTrue();
            assertThat(assessment.standing()).isEqualTo(PrerequisiteStanding.WAIVED);
        });
    }

    @Test
    void passedCourseworkPrerequisiteIgnoresMinimumForRegularStanding() {
        prerequisites(prerequisiteWithMinimum(RequirementType.PASSED_COURSEWORK, "8"));
        studentHas(10L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void equivalenceWithOriginScoreBelowMinimumIsNotMet() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentHasEquivalence(10L, "4");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.standing()).isEqualTo(PrerequisiteStanding.APPROVED);
            assertThat(assessment.met()).isFalse();
            assertThat(assessment.approvedScore()).isEqualTo(Grade.of(new BigDecimal("4")));
        });
    }

    @Test
    void equivalenceWithOriginScoreEqualToMinimumIsMet() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentHasEquivalence(10L, "6");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void equivalenceWithoutOriginScoreIsMetEvenWithMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentHasEquivalence(10L, null);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void approvalControlStaysIndependentOfMinimumGrade() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "2");

        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isTrue();
    }

    @Test
    void approvedAttemptWithoutScoreIsMetEvenWhenEquivalenceHasScoreBelowMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, (String) null);
        studentHasEquivalence(10L, "4");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.met()).isTrue();
            assertThat(assessment.approvedScore()).isNull();
        });
    }

    @Test
    void equivalenceWithoutScoreIsMetEvenWhenApprovedAttemptIsBelowMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "4");
        studentHasEquivalence(10L, null);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void bestScoreBetweenApprovedAttemptAndEquivalenceDecides() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "7");
        studentHasEquivalence(10L, "4");

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).singleElement().satisfies(assessment -> {
            assertThat(assessment.met()).isTrue();
            assertThat(assessment.approvedScore()).isEqualTo(Grade.of(new BigDecimal("7")));
        });
    }

    @Test
    void finalExamListsAnApprovedPrerequisiteBelowTheMinimumWithItsScore() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "5");

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).singleElement()
                .satisfies(assessment -> {
                    assertThat(assessment.standing()).isEqualTo(PrerequisiteStanding.APPROVED);
                    assertThat(assessment.minGrade()).isEqualTo(Grade.of(new BigDecimal("6")));
                    assertThat(assessment.approvedScore()).isEqualTo(Grade.of(new BigDecimal("5")));
                });
    }

    @Test
    void finalExamDoesNotListAnApprovedPrerequisiteWithScoreEqualToTheMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "6");

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void finalExamDoesNotListAnApprovedPrerequisiteWithoutScore() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, (String) null);

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void finalExamStillListsARegularPrerequisiteOfPassedCourseworkType() {
        prerequisites(prerequisiteWithMinimum(RequirementType.PASSED_COURSEWORK, "6"));
        studentHas(10L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).hasSize(1);
    }

    @Test
    void finalExamIsEmptyWhenAWaiverCoversTheMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "2");
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void approvalControlListStaysIndependentOfTheMinimum() {
        prerequisites(prerequisiteWithMinimum(RequirementType.APPROVED, "6"));
        studentApprovedWithScore(10L, "2");

        assertThat(evaluator.findUnmetForApprovalOrEmptyOutsidePlan(ENROLLMENT_ID, SUBJECT_ID)).isEmpty();
    }

    @Test
    void finalExamIsEmptyWhenTheSubjectIsOutsideThePlan() {
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 99L)).thenReturn(Optional.empty());

        assertThat(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(ENROLLMENT_ID, 99L)).isEmpty();
    }
}
