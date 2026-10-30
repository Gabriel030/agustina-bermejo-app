package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
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

    @Test
    void aFailingCourseDoesNotPreventExpiringTheWaitlistOfTheOthers() {
        CourseCatalogFinder courseCatalogFinder = mock(CourseCatalogFinder.class);
        CourseWaitlistExpiration courseWaitlistExpiration = mock(CourseWaitlistExpiration.class);
        when(courseCatalogFinder.findCourseIdsWithWaitlistDeadlinePassed(any(LocalDate.class))).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("fila inconsistente")).when(courseWaitlistExpiration).expire(eq(1L));

        new CourseEnrollmentWaitlistExpiryJob(courseCatalogFinder, courseWaitlistExpiration, java.time.Clock.systemDefaultZone()).expireWaitlists();

        verify(courseWaitlistExpiration).expire(1L);
        verify(courseWaitlistExpiration).expire(2L);
    }
}
