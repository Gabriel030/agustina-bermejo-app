package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.api.HolidayFinder;
import com.red_escuela.backend.academicstructure.api.InstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceSectionAccessTest {

    private final SectionTeachingFinder teachingFinder = mock(SectionTeachingFinder.class);
    private final SectionFinder sectionFinder = mock(SectionFinder.class);
    private final InstructorFinder instructorFinder = mock(InstructorFinder.class);
    private final HolidayFinder holidayFinder = mock(HolidayFinder.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AttendanceSectionAccess access = new AttendanceSectionAccess(
            teachingFinder, sectionFinder, instructorFinder, holidayFinder, entityManager);

    @Test
    void ownershipChecksAskTheTeachingFinderWithTheMatchingKind() {
        when(teachingFinder.teaches(SectionKind.PROGRAM, 1L, 2L)).thenReturn(true);
        when(teachingFinder.teaches(SectionKind.COURSE, 1L, 2L)).thenReturn(false);

        assertThat(access.isOwnProgramSection(1L, 2L)).isTrue();
        assertThat(access.isOwnCourseSection(1L, 2L)).isFalse();
    }

    @Test
    void requireOwnSectionPropagatesTheNotOwnSectionFailure() {
        doThrow(new NotOwnSectionException(2L, 1L)).when(teachingFinder).requireTeaches(SectionKind.COURSE, 1L, 2L);

        assertThatThrownBy(() -> access.requireOwnCourseSection(1L, 2L)).isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void optionalOwnershipCheckIsSkippedWhenNoInstructorIsGiven() {
        access.requireOwnProgramSectionIfInstructorGiven(1L, null);
        access.requireOwnCourseSectionIfInstructorGiven(1L, null);

        verify(teachingFinder, never()).requireTeaches(any(), any(), any());
    }

    @Test
    void optionalOwnershipCheckIsEnforcedWhenAnInstructorIsGiven() {
        access.requireOwnProgramSectionIfInstructorGiven(1L, 2L);

        verify(teachingFinder).requireTeaches(SectionKind.PROGRAM, 1L, 2L);
    }

    @Test
    void missingSectionFailsBeforeBuildingAReference() {
        doThrow(new SectionNotFoundException(9L)).when(sectionFinder).requireExists(SectionKind.PROGRAM, 9L);

        assertThatThrownBy(() -> access.findProgramSection(9L)).isInstanceOf(SectionNotFoundException.class);
        verify(entityManager, never()).getReference(any(), any());
    }

    @Test
    void existingSectionsAreReturnedAsReferences() {
        SubjectSection program = new SubjectSection();
        CourseSubjectSection course = new CourseSubjectSection();
        when(entityManager.getReference(SubjectSection.class, 3L)).thenReturn(program);
        when(entityManager.getReference(CourseSubjectSection.class, 4L)).thenReturn(course);

        assertThat(access.findProgramSection(3L)).isSameAs(program);
        assertThat(access.findCourseSection(4L)).isSameAs(course);
        verify(sectionFinder).requireExists(SectionKind.PROGRAM, 3L);
        verify(sectionFinder).requireExists(SectionKind.COURSE, 4L);
    }

    @Test
    void missingInstructorFailsBeforeBuildingAReference() {
        doThrow(new InstructorNotFoundException(7L)).when(instructorFinder).requireExists(7L);

        assertThatThrownBy(() -> access.resolveInstructor(7L)).isInstanceOf(InstructorNotFoundException.class);
        verify(entityManager, never()).getReference(any(), any());
    }

    @Test
    void existingInstructorIsReturnedAsReference() {
        Instructor instructor = new Instructor();
        when(entityManager.getReference(Instructor.class, 7L)).thenReturn(instructor);

        assertThat(access.resolveInstructor(7L)).isSameAs(instructor);
    }

    @Test
    void holidayLookupDelegatesToTheHolidayFinder() {
        LocalDate date = LocalDate.of(2026, 5, 25);
        when(holidayFinder.isHoliday(date)).thenReturn(true);

        assertThat(access.isHoliday(date)).isTrue();
        assertThat(access.isHoliday(date.plusDays(1))).isFalse();
    }
}
