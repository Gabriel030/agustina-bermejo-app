package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseSectionTermEndResolverTest {

    private final CourseSectionTermEndResolver resolver = new CourseSectionTermEndResolver();

    @Test
    void termEndComesFromTheCycleWhenPresent() {
        CourseSubjectSection section = mock(CourseSubjectSection.class, RETURNS_DEEP_STUBS);
        when(section.getAcademicOfferingCycle().getTerm().getEndDate()).thenReturn(LocalDateTime.of(2026, 12, 20, 23, 59));
        when(section.getTerm().getEndDate()).thenReturn(LocalDateTime.of(2026, 7, 1, 0, 0));

        assertThat(resolver.termEndDate(section)).isEqualTo(LocalDate.of(2026, 12, 20));
    }

    @Test
    void termEndFallsBackToTheSectionTermWithoutCycle() {
        CourseSubjectSection section = mock(CourseSubjectSection.class, RETURNS_DEEP_STUBS);
        when(section.getAcademicOfferingCycle()).thenReturn(null);
        when(section.getTerm().getEndDate()).thenReturn(LocalDateTime.of(2026, 7, 1, 0, 0));

        assertThat(resolver.termEndDate(section)).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
