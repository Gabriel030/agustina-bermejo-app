package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosureOutcomeFreeAndConditionalReproductionTest {

    private final SectionEnrollmentOutcomeRecorder recorder = mock(SectionEnrollmentOutcomeRecorder.class);
    private final SectionEnrollmentFinder finder = mock(SectionEnrollmentFinder.class);
    private final GraduationRequestTriggerPort graduation = mock(GraduationRequestTriggerPort.class);
    private final ClosureOutcomeApplier applier = new ClosureOutcomeApplier(recorder, finder, graduation);

    private void inProgressProgramEnrollment(Long id) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setId(70L + id);
        enrollment.setProgramEnrollment(programEnrollment);
        when(finder.refreshProgramEnrollment(id)).thenReturn(enrollment);
        when(finder.findProgramEnrollment(id)).thenReturn(enrollment);
    }

    private static boolean leavesEnrollmentOpen(EnrollmentStatus status) {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.IN_PROGRESS;
    }

    private void applyClosureWith(ClosureFinalCondition condition) {
        inProgressProgramEnrollment(1L);
        applier.applyClosedResults(SectionKind.PROGRAM, List.of(
                CommissionClosureStudent.builder().enrollmentId(1L).finalCondition(condition).build()));
    }

    @Test
    @Disabled("E34-A-S1")
    void h1FreeStudentLeavesTheOpenStatusWhenTheClosureIsApplied() {
        applyClosureWith(ClosureFinalCondition.FREE);

        verify(recorder).recordProgramOutcome(eq(1L), argThat(status -> !leavesEnrollmentOpen(status)),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    @Test
    @Disabled("E34-A-S1")
    void h1ConditionalPromotionStudentLeavesTheOpenStatusWhenTheClosureIsApplied() {
        applyClosureWith(ClosureFinalCondition.PROMOTION_CONDITIONAL);

        verify(recorder).recordProgramOutcome(eq(1L), argThat(status -> !leavesEnrollmentOpen(status)),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }
}
