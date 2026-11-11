package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClosureOutcomeApplierTest {

    private final SectionEnrollmentOutcomeRecorder recorder = mock(SectionEnrollmentOutcomeRecorder.class);
    private final SectionEnrollmentFinder finder = mock(SectionEnrollmentFinder.class);
    private final GraduationRequestTriggerPort graduation = mock(GraduationRequestTriggerPort.class);
    private final ClosureOutcomeApplier applier = new ClosureOutcomeApplier(recorder, finder, graduation);

    private SubjectSectionEnrollment programEnrollment(Long id, EnrollmentStatus status, Long programEnrollmentId) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStatus(status);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setId(programEnrollmentId);
        enrollment.setProgramEnrollment(programEnrollment);
        when(finder.refreshProgramEnrollment(id)).thenReturn(enrollment);
        when(finder.findProgramEnrollment(id)).thenReturn(enrollment);
        return enrollment;
    }

    private CommissionClosureStudent row(Long enrollmentId, ClosureFinalCondition condition, String grade) {
        return CommissionClosureStudent.builder().enrollmentId(enrollmentId).finalCondition(condition)
                .promotionGrade(grade == null ? null : Grade.of(new BigDecimal(grade))).build();
    }

    @Test
    void promotedStudentsAreApprovedWithTheirDecimalGradeAndGraduationIsRequestedOnce() {
        Grade grade = Grade.of(new BigDecimal("8.50"));
        programEnrollment(1L, EnrollmentStatus.IN_PROGRESS, 77L);

        applier.applyClosedResults(SectionKind.PROGRAM, List.of(row(1L, ClosureFinalCondition.PROMOTES, "8.50")));

        verify(recorder).recordProgramOutcome(1L, EnrollmentStatus.APPROVED, grade, FinalGradeType.PROMOTION, true);
        verify(graduation, times(1)).requestGraduationIfEligible(77L);
    }

    @Test
    void finalGoersBecomeRegularAndFailedBecomeFailedFlaggingTheGraduationRequestAsOutdated() {
        programEnrollment(1L, EnrollmentStatus.IN_PROGRESS, 71L);
        programEnrollment(2L, EnrollmentStatus.IN_PROGRESS, 72L);
        applier.applyClosedResults(SectionKind.PROGRAM, List.of(
                row(1L, ClosureFinalCondition.GOES_TO_FINAL, null), row(2L, ClosureFinalCondition.FAILED, null)));

        verify(recorder).recordProgramOutcome(1L, EnrollmentStatus.REGULAR, null, null, true);
        verify(recorder).recordProgramOutcome(2L, EnrollmentStatus.FAILED, null, null, true);
        verify(graduation).flagGraduationRequestOutdated(71L);
        verify(graduation).flagGraduationRequestOutdated(72L);
        verify(graduation, never()).requestGraduationIfEligible(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void studentsWhoWithdrewOrGraduatedByFinalExamSinceTheSnapshotAreSkipped() {
        programEnrollment(1L, EnrollmentStatus.WITHDRAWN, 71L);
        SubjectSectionEnrollment approvedByFinal = programEnrollment(2L, EnrollmentStatus.APPROVED, 72L);
        approvedByFinal.setFinalGradeType(FinalGradeType.FINAL_EXAM);

        applier.applyClosedResults(SectionKind.PROGRAM, List.of(
                row(1L, ClosureFinalCondition.PROMOTES, "8.00"), row(2L, ClosureFinalCondition.GOES_TO_FINAL, null)));

        verifyNoInteractions(recorder, graduation);
    }

    @Test
    void freeStudentsAreLeftUntouchedAndCourseSectionsNeverRequestGraduation() {
        CourseSubjectSectionEnrollment courseEnrollment = new CourseSubjectSectionEnrollment();
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        when(finder.refreshCourseEnrollment(org.mockito.ArgumentMatchers.anyLong())).thenReturn(courseEnrollment);
        applier.applyClosedResults(SectionKind.COURSE, List.of(
                row(1L, ClosureFinalCondition.FREE, null), row(2L, ClosureFinalCondition.PROMOTES, "9.00")));

        verify(recorder, never()).recordProgramOutcome(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(recorder).recordCourseOutcome(2L, EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("9.00")), FinalGradeType.PROMOTION, true);
        verify(recorder, never()).recordCourseOutcome(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(graduation);
    }
}
