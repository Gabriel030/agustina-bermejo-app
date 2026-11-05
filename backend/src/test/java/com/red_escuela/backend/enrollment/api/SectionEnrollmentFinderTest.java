package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
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
class SectionEnrollmentFinderTest {

    @Mock
    private SubjectSectionEnrollmentRepository programEnrollmentRepository;
    @Mock
    private CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    @InjectMocks
    private SectionEnrollmentFinder finder;

    private Student studentWithId(Long id) {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(id);
        return student;
    }

    private SubjectSectionEnrollment programEnrollmentOf(Long studentId) {
        Student student = studentWithId(studentId);
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    private CourseSubjectSectionEnrollment courseEnrollmentOf(Long studentId) {
        Student student = studentWithId(studentId);
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    @Test
    void findProgramEnrollmentThrowsWhenMissing() {
        when(programEnrollmentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findProgramEnrollment(5L))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void findCourseEnrollmentThrowsWhenMissing() {
        when(courseEnrollmentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findCourseEnrollment(5L))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void requireProgramEnrollmentOfReturnsEnrollmentOfTheStudent() {
        SubjectSectionEnrollment enrollment = programEnrollmentOf(7L);
        when(programEnrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireProgramEnrollmentOf(5L, 7L)).isSameAs(enrollment);
    }

    @Test
    void requireProgramEnrollmentOfHidesEnrollmentOfAnotherStudent() {
        SubjectSectionEnrollment enrollment = programEnrollmentOf(7L);
        when(programEnrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> finder.requireProgramEnrollmentOf(5L, 8L))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void requireCourseEnrollmentOfReturnsEnrollmentOfTheStudent() {
        CourseSubjectSectionEnrollment enrollment = courseEnrollmentOf(7L);
        when(courseEnrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.requireCourseEnrollmentOf(5L, 7L)).isSameAs(enrollment);
    }

    @Test
    void requireCourseEnrollmentOfHidesEnrollmentOfAnotherStudent() {
        CourseSubjectSectionEnrollment enrollment = courseEnrollmentOf(7L);
        when(courseEnrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> finder.requireCourseEnrollmentOf(5L, 8L))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void findActiveInProgramSectionQueriesEnrolledAndInProgress() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(programEnrollmentRepository.findBySubjectSectionIdAndStatusIn(3L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))).thenReturn(List.of(enrollment));

        assertThat(finder.findActiveInProgramSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findActiveInCourseSectionQueriesEnrolledAndInProgress() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(courseEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(3L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))).thenReturn(List.of(enrollment));

        assertThat(finder.findActiveInCourseSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findMembersOfProgramSectionQueriesTheFiveMemberStatuses() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(programEnrollmentRepository.findBySubjectSectionIdAndStatusIn(3L, List.of(
                EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR,
                EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED))).thenReturn(List.of(enrollment));

        assertThat(finder.findMembersOfProgramSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findMembersOfCourseSectionQueriesTheFiveMemberStatuses() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(courseEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(3L, List.of(
                EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR,
                EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED))).thenReturn(List.of(enrollment));

        assertThat(finder.findMembersOfCourseSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findActiveWithStudentInProgramSectionQueriesEnrolledAndInProgressWithStudentLoaded() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(programEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(3L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))).thenReturn(List.of(enrollment));

        assertThat(finder.findActiveWithStudentInProgramSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findActiveWithStudentInCourseSectionQueriesEnrolledAndInProgressWithStudentLoaded() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(courseEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(3L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))).thenReturn(List.of(enrollment));

        assertThat(finder.findActiveWithStudentInCourseSection(3L)).containsExactly(enrollment);
    }

    @Test
    void findProgramEnrollmentIfPresentReturnsEmptyWhenMissing() {
        when(programEnrollmentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(finder.findProgramEnrollmentIfPresent(5L)).isEmpty();
    }

    @Test
    void findProgramEnrollmentIfPresentReturnsTheEnrollment() {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(programEnrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.findProgramEnrollmentIfPresent(5L)).containsSame(enrollment);
    }

    @Test
    void findCourseEnrollmentIfPresentReturnsEmptyWhenMissing() {
        when(courseEnrollmentRepository.findById(6L)).thenReturn(Optional.empty());

        assertThat(finder.findCourseEnrollmentIfPresent(6L)).isEmpty();
    }

    @Test
    void findCourseEnrollmentIfPresentReturnsTheEnrollment() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(courseEnrollmentRepository.findById(6L)).thenReturn(Optional.of(enrollment));

        assertThat(finder.findCourseEnrollmentIfPresent(6L)).containsSame(enrollment);
    }
}
