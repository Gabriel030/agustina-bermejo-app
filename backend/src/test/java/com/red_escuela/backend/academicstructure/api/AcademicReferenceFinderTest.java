package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicReferenceFinderTest {

    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private CourseSubjectRepository courseSubjectRepository;
    @Mock
    private TermRepository termRepository;
    @Mock
    private InstructorRepository instructorRepository;
    @Mock
    private ProgramRepository programRepository;
    @InjectMocks
    private AcademicReferenceFinder finder;

    @Test
    void findSubjectReturnsTheStoredSubject() {
        Subject subject = new Subject();
        when(subjectRepository.findById(3L)).thenReturn(Optional.of(subject));

        assertThat(finder.findSubject(3L)).isSameAs(subject);
    }

    @Test
    void findSubjectFailsWhenTheSubjectDoesNotExist() {
        when(subjectRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findSubject(3L)).isInstanceOf(SubjectNotFoundException.class);
    }

    @Test
    void findProgramReturnsTheStoredProgram() {
        Program program = new Program();
        when(programRepository.findById(5L)).thenReturn(Optional.of(program));

        assertThat(finder.findProgram(5L)).isSameAs(program);
    }

    @Test
    void findProgramFailsWhenTheProgramDoesNotExist() {
        when(programRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findProgram(5L)).isInstanceOf(ProgramNotFoundException.class);
    }

    @Test
    void findCourseSubjectReturnsTheStoredCourseSubject() {
        CourseSubject courseSubject = new CourseSubject();
        when(courseSubjectRepository.findById(4L)).thenReturn(Optional.of(courseSubject));

        assertThat(finder.findCourseSubject(4L)).isSameAs(courseSubject);
    }

    @Test
    void findCourseSubjectFailsWhenTheCourseSubjectDoesNotExist() {
        when(courseSubjectRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findCourseSubject(4L)).isInstanceOf(CourseSubjectNotFoundException.class);
    }

    @Test
    void findTermReturnsTheStoredTerm() {
        Term term = new Term();
        when(termRepository.findById(5L)).thenReturn(Optional.of(term));

        assertThat(finder.findTerm(5L)).isSameAs(term);
    }

    @Test
    void findTermFailsWhenTheTermDoesNotExist() {
        when(termRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findTerm(5L)).isInstanceOf(TermNotFoundException.class);
    }

    @Test
    void findInstructorReturnsTheStoredInstructor() {
        Instructor instructor = new Instructor();
        when(instructorRepository.findById(6L)).thenReturn(Optional.of(instructor));

        assertThat(finder.findInstructor(6L)).isSameAs(instructor);
    }

    @Test
    void findInstructorFailsWhenTheInstructorDoesNotExist() {
        when(instructorRepository.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findInstructor(6L)).isInstanceOf(InstructorNotFoundException.class);
    }
}
