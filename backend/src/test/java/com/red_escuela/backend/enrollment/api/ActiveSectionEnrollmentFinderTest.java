package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.CourseSectionTermEndResolver;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveSectionEnrollmentFinderTest {

    private static final List<EnrollmentStatus> ACTIVE =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository programRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final CourseSubjectSectionEnrollmentRepository courseRepository = mock(CourseSubjectSectionEnrollmentRepository.class);
    private final CourseSectionTermEndResolver termEndResolver = mock(CourseSectionTermEndResolver.class);
    private final ActiveSectionEnrollmentFinder finder = new ActiveSectionEnrollmentFinder(programRepository, courseRepository, termEndResolver);

    @Test
    void programSectionEnrollmentsAreQueriedWithTheActiveStatuses() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(enrollment.getId()).thenReturn(8L);
        when(programRepository.findByProgramEnrollmentIdAndStatusIn(17L, ACTIVE)).thenReturn(List.of(enrollment));

        assertThat(finder.activeProgramSectionEnrollmentIds(17L)).containsExactly(8L);
    }

    @Test
    void courseSectionsCarryTheTermEndResolvedForEachSection() {
        CourseSubjectSectionEnrollment first = mock(CourseSubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        CourseSubjectSectionEnrollment second = mock(CourseSubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(first.getId()).thenReturn(4L);
        when(second.getId()).thenReturn(6L);
        when(termEndResolver.termEndDate(first.getCourseSubjectSection())).thenReturn(LocalDate.of(2026, 12, 20));
        when(termEndResolver.termEndDate(second.getCourseSubjectSection())).thenReturn(LocalDate.of(2026, 7, 1));
        when(courseRepository.findByCourseEnrollmentIdAndStatusIn(5L, ACTIVE)).thenReturn(List.of(first, second));

        assertThat(finder.activeCourseSections(5L)).containsExactly(
                new ActiveCourseSectionView(4L, LocalDate.of(2026, 12, 20)),
                new ActiveCourseSectionView(6L, LocalDate.of(2026, 7, 1)));
    }
}
