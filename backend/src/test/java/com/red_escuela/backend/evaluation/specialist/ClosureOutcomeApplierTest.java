package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
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

    private CommissionClosureStudent row(Long enrollmentId, ClosureFinalCondition condition, String grade) {
        return CommissionClosureStudent.builder().enrollmentId(enrollmentId).finalCondition(condition)
                .promotionGrade(grade == null ? null : Grade.of(new BigDecimal(grade))).build();
    }

    @Test
    void promotedStudentsAreApprovedWithTheirDecimalGradeAndGraduationIsRequestedOnce() {
        Grade grade = Grade.of(new BigDecimal("8.50"));
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setId(77L);
        enrollment.setProgramEnrollment(programEnrollment);
        when(finder.findProgramEnrollment(1L)).thenReturn(enrollment);

        applier.applyClosedResults(SectionKind.PROGRAM, List.of(row(1L, ClosureFinalCondition.PROMOTES, "8.50")));

        verify(recorder).recordProgramOutcome(1L, EnrollmentStatus.APPROVED, grade, FinalGradeType.PROMOTION, true);
        verify(graduation, times(1)).requestGraduationIfEligible(77L);
    }

    @Test
    void finalGoersBecomeRegularAndFailedBecomeFailedWithoutRequestingGraduation() {
        applier.applyClosedResults(SectionKind.PROGRAM, List.of(
                row(1L, ClosureFinalCondition.GOES_TO_FINAL, null), row(2L, ClosureFinalCondition.FAILED, null)));

        verify(recorder).recordProgramOutcome(1L, EnrollmentStatus.REGULAR, null, null, true);
        verify(recorder).recordProgramOutcome(2L, EnrollmentStatus.FAILED, null, null, true);
        verifyNoInteractions(graduation);
    }

    @Test
    void freeStudentsAreLeftUntouchedAndCourseSectionsNeverRequestGraduation() {
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
