package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramEnrollmentFinderTest {

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @InjectMocks
    private ProgramEnrollmentFinder finder;

    private ProgramEnrollment enrollmentOf(Long studentId) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(studentId);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    @Test
    void findReturnsEnrollment() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.find(4L)).isSameAs(enrollment);
    }

    @Test
    void findThrowsWhenMissing() {
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.find(4L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void requireOwnedByReturnsEnrollmentOfTheStudent() {
        ProgramEnrollment enrollment = enrollmentOf(7L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireOwnedBy(4L, 7L)).isSameAs(enrollment);
    }

    @Test
    void requireOwnedByHidesEnrollmentOfAnotherStudent() {
        ProgramEnrollment enrollment = enrollmentOf(7L);
        when(programEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> finder.requireOwnedBy(4L, 8L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void findByStudentDelegatesToRepository() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findByStudentId(7L)).thenReturn(List.of(enrollment));

        assertThat(finder.findByStudent(7L)).containsExactly(enrollment);
    }

    @Test
    void findAllByIdDelegatesToRepository() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(programEnrollmentRepository.findAllById(List.of(4L, 5L))).thenReturn(List.of(enrollment));

        assertThat(finder.findAllById(List.of(4L, 5L))).containsExactly(enrollment);
    }
}
