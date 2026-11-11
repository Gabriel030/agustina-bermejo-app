package com.red_escuela.backend.enrollment.api;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private final ProgramPrerequisiteEvaluator evaluator = new ProgramPrerequisiteEvaluator(
            enrollmentRepository, sectionRepository, approvalRepository, curriculumFinder);

    @BeforeEach
    void setUp() {
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

    private void studentHas(Long prerequisiteSubjectId, EnrollmentStatus... statuses) {
        List<SubjectSectionEnrollment> enrollments = java.util.Arrays.stream(statuses).map(status -> {
            SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
            when(enrollment.getStatus()).thenReturn(status);
            return enrollment;
        }).toList();
        when(sectionRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                eq(ENROLLMENT_ID), eq(prerequisiteSubjectId), any())).thenReturn(enrollments);
    }

    @Test
    void approvedRequirementWithOnlyRegularStandingIsNotMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED));
        studentHas(10L, EnrollmentStatus.REGULAR);

        List<PrerequisiteAssessment> result = evaluator.assess(ENROLLMENT_ID, SUBJECT_ID);

        assertThat(result).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.APPROVED, PrerequisiteStanding.REGULAR, false));
    }

    @Test
    void passedCourseworkRequirementWithRegularStandingIsMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK));
        studentHas(10L, EnrollmentStatus.REGULAR);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).extracting(PrerequisiteAssessment::met).containsExactly(true);
    }

    @Test
    void aPrerequisiteTheStudentNeverTookIsNotMet() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.PASSED_COURSEWORK));
        studentHas(10L);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.PASSED_COURSEWORK, PrerequisiteStanding.NOT_TAKEN, false));
    }

    @Test
    void anApprovedWaiverMarksEveryPrerequisiteAsWaivedWithoutQueryingTheStudentHistory() {
        prerequisites(new PrerequisiteView(10L, "Algebra I", RequirementType.APPROVED));
        when(approvalRepository.hasApproved(ENROLLMENT_ID, SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER)).thenReturn(true);

        assertThat(evaluator.assess(ENROLLMENT_ID, SUBJECT_ID)).containsExactly(new PrerequisiteAssessment(
                10L, "Algebra I", RequirementType.APPROVED, PrerequisiteStanding.WAIVED, true));
        verify(sectionRepository, never()).findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(any(), any(), any());
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
}
