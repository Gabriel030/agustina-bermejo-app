package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramPlanSubjectGuardTest {

    @Mock
    private ProgramCurriculumFinder programCurriculumFinder;
    @InjectMocks
    private ProgramPlanSubjectGuard guard;

    @Test
    void subjectOutsideThePlanIsRejected() {
        when(programCurriculumFinder.subjectBelongsToProgram(5L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireSubjectInPlan(5L, 9L))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void subjectInThePlanIsAccepted() {
        when(programCurriculumFinder.subjectBelongsToProgram(5L, 9L)).thenReturn(true);

        assertThatCode(() -> guard.requireSubjectInPlan(5L, 9L)).doesNotThrowAnyException();
    }
}
