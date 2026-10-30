package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramSubjectReferenceFinderTest {

    @Mock
    private AcademicReferenceFinder academicReferenceFinder;
    @Mock
    private ProgramPlanSubjectGuard programPlanSubjectGuard;
    @InjectMocks
    private ProgramSubjectReferenceFinder finder;

    @Test
    void findProgramDelegatesToTheAcademicReferences() {
        Program program = new Program();
        when(academicReferenceFinder.findProgram(5L)).thenReturn(program);

        assertThat(finder.findProgram(5L)).isSameAs(program);
    }

    @Test
    void findSubjectInProgramPlanReturnsTheSubjectWhenItBelongsToThePlan() {
        Subject subject = new Subject();
        subject.setId(7L);
        when(academicReferenceFinder.findSubject(7L)).thenReturn(subject);

        assertThat(finder.findSubjectInProgramPlan(5L, 7L)).isSameAs(subject);
    }

    @Test
    void findSubjectInProgramPlanFailsWhenTheSubjectIsOutsideThePlan() {
        Subject subject = new Subject();
        subject.setId(7L);
        when(academicReferenceFinder.findSubject(7L)).thenReturn(subject);
        doThrow(new SubjectNotInProgramPlanException(7L, 5L)).when(programPlanSubjectGuard).requireSubjectInPlan(5L, 7L);

        assertThatThrownBy(() -> finder.findSubjectInProgramPlan(5L, 7L))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }
}
