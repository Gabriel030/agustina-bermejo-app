package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseEnrollmentWaitlistExpiryJobTest {

    private Course courseWithPassedDeadline(long id) {
        Course course = mock(Course.class);
        when(course.getId()).thenReturn(id);
        when(course.waitlistDeadlineHasPassed(any(LocalDate.class))).thenReturn(true);
        return course;
    }

    @Test
    void aFailingCourseDoesNotPreventExpiringTheWaitlistOfTheOthers() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseWaitlistExpiration courseWaitlistExpiration = mock(CourseWaitlistExpiration.class);
        Course failing = courseWithPassedDeadline(1L);
        Course healthy = courseWithPassedDeadline(2L);
        when(courseRepository.findByWaitlistDeadlineOffsetDaysIsNotNull()).thenReturn(List.of(failing, healthy));
        doThrow(new IllegalStateException("fila inconsistente")).when(courseWaitlistExpiration).expire(eq(1L));

        new CourseEnrollmentWaitlistExpiryJob(courseRepository, courseWaitlistExpiration).expireWaitlists();

        verify(courseWaitlistExpiration).expire(1L);
        verify(courseWaitlistExpiration).expire(2L);
    }
}
