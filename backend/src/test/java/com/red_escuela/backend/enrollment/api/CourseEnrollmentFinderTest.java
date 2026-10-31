package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentFinderTest {

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @InjectMocks
    private CourseEnrollmentFinder finder;

    private CourseEnrollment enrollmentWithId(Long id) {
        CourseEnrollment enrollment = mock(CourseEnrollment.class);
        when(enrollment.getId()).thenReturn(id);
        return enrollment;
    }

    @Test
    void requireByIdReturnsTheEnrollment() {
        CourseEnrollment enrollment = mock(CourseEnrollment.class);
        when(courseEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireById(4L)).isSameAs(enrollment);
    }

    @Test
    void requireByIdFailsWhenTheEnrollmentDoesNotExist() {
        when(courseEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requireById(4L)).isInstanceOf(CourseEnrollmentNotFoundException.class);
    }

    @Test
    void requireBillingViewByIdExposesIdsStatusAndWithdrawalDates() {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(7L);
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(3L);
        CourseEnrollment enrollment = enrollmentWithId(4L);
        when(enrollment.getStudent()).thenReturn(student);
        when(enrollment.getCourse()).thenReturn(course);
        when(enrollment.getStatus()).thenReturn(EnrollmentStatus.values()[0]);
        when(enrollment.getWithdrawalEffectiveDate()).thenReturn(LocalDate.of(2026, 3, 1));
        when(enrollment.getWithdrawalRegisteredOn()).thenReturn(LocalDate.of(2026, 2, 20));
        when(courseEnrollmentRepository.findById(4L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireBillingViewById(4L)).isEqualTo(new EnrollmentBillingView(4L, 7L, null, 3L, null,
                null, EnrollmentStatus.values()[0], LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 20)));
    }

    @Test
    void requireBillingViewByIdFailsWhenTheEnrollmentDoesNotExist() {
        when(courseEnrollmentRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requireBillingViewById(4L)).isInstanceOf(CourseEnrollmentNotFoundException.class);
    }

    @Test
    void findIdsByStudentIdReturnsOnlyTheIdsOfThatStudentEnrollments() {
        CourseEnrollment first = enrollmentWithId(4L);
        CourseEnrollment second = enrollmentWithId(6L);
        when(courseEnrollmentRepository.findByStudentId(7L)).thenReturn(List.of(first, second));

        assertThat(finder.findIdsByStudentId(7L)).containsExactly(4L, 6L);
    }
}
