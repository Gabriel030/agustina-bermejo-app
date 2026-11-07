package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.api.CurrentInstructorIdFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionMembershipTest {

    @Mock
    private SectionTeachingFinder sectionTeachingFinder;
    @Mock
    private SectionEnrollmentFinder sectionEnrollmentFinder;
    @Mock
    private CurrentInstructorIdFinder currentInstructorIdFinder;

    private SectionMembershipChecker membership() {
        return new SectionMembershipChecker(sectionTeachingFinder, sectionEnrollmentFinder, currentInstructorIdFinder);
    }

    @Test
    void programSectionInstructorPassesTheTeachingCheckAsProgramKind() {
        when(sectionTeachingFinder.teaches(SectionKind.PROGRAM, 1L, 2L)).thenReturn(true);

        assertThatCode(() -> membership().requireInstructorOf(SectionType.PROGRAM, 1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void courseSectionInstructorPassesTheTeachingCheckAsCourseKind() {
        when(sectionTeachingFinder.teaches(SectionKind.COURSE, 1L, 2L)).thenReturn(true);

        assertThatCode(() -> membership().requireInstructorOf(SectionType.COURSE, 1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void instructorOfAnotherSectionIsForbidden() {
        when(sectionTeachingFinder.teaches(SectionKind.COURSE, 1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> membership().requireInstructorOf(SectionType.COURSE, 1L, 2L))
                .isInstanceOf(NotSectionInstructorException.class);
    }

    @Test
    void requireCurrentInstructorOfDelegatesWithTheResolvedInstructorId() {
        when(currentInstructorIdFinder.currentInstructorId()).thenReturn(7L);
        when(sectionTeachingFinder.teaches(SectionKind.COURSE, 3L, 7L)).thenReturn(true);

        assertThatCode(() -> membership().requireCurrentInstructorOf(SectionType.COURSE, 3L)).doesNotThrowAnyException();
        verify(sectionTeachingFinder).teaches(SectionKind.COURSE, 3L, 7L);
    }

    @Test
    void rejectsWhenCurrentInstructorDoesNotTeachTheSection() {
        when(currentInstructorIdFinder.currentInstructorId()).thenReturn(7L);
        when(sectionTeachingFinder.teaches(SectionKind.PROGRAM, 3L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> membership().requireCurrentInstructorOf(SectionType.PROGRAM, 3L))
                .isInstanceOf(NotSectionInstructorException.class);
    }

    @Test
    void studentsOfProgramSectionAreTakenFromProgramMembers() {
        Student student = new Student();
        student.setId(7L);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        when(sectionEnrollmentFinder.findMembersOfProgramSection(1L)).thenReturn(List.of(enrollment));

        assertThat(membership().studentsOf(SectionType.PROGRAM, 1L)).containsExactly(student);
    }

    @Test
    void studentsOfCourseSectionAreTakenFromCourseMembers() {
        Student student = new Student();
        student.setId(8L);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        when(sectionEnrollmentFinder.findMembersOfCourseSection(1L)).thenReturn(List.of(enrollment));

        assertThat(membership().studentsOf(SectionType.COURSE, 1L)).containsExactly(student);
    }
}
