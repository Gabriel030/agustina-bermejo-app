package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramPlanSubjectGuardTest {

    @Mock
    private ProgramCurriculumFinder programCurriculumFinder;
    @InjectMocks
    private ProgramPlanSubjectGuard guard;

    private ProgramEnrollment enrollmentInPlan(Long planId, Long programId) {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        CurriculumPlan plan = mock(CurriculumPlan.class);
        Program program = mock(Program.class);
        lenient().when(enrollment.getPlan()).thenReturn(plan);
        lenient().when(plan.getId()).thenReturn(planId);
        lenient().when(enrollment.getProgram()).thenReturn(program);
        lenient().when(program.getId()).thenReturn(programId);
        return enrollment;
    }

    @Test
    void subjectOutsideThePlanIsRejected() {
        when(programCurriculumFinder.subjectBelongsToPlan(17L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireSubjectInPlan(enrollmentInPlan(17L, 5L), 9L))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void subjectInThePlanIsAccepted() {
        when(programCurriculumFinder.subjectBelongsToPlan(17L, 9L)).thenReturn(true);

        assertThatCode(() -> guard.requireSubjectInPlan(enrollmentInPlan(17L, 5L), 9L)).doesNotThrowAnyException();
    }
}
