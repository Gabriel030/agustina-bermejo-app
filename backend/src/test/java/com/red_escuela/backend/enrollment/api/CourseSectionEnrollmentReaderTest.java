package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorView;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseSectionEnrollmentReaderTest {

    private final CourseSubjectSectionEnrollmentRepository repository = mock(CourseSubjectSectionEnrollmentRepository.class);
    private final SectionInstructorFinder sectionInstructorFinder = mock(SectionInstructorFinder.class);
    private final CourseSectionEnrollmentReader reader = new CourseSectionEnrollmentReader(repository, sectionInstructorFinder);

    @Test
    void listsAllTheEnrollmentsOfTheStudentWhenNoCourseEnrollmentIsGiven() {
        when(repository.findByStudentIdOrderByIdAsc(3L)).thenReturn(List.of(enrollmentOf(10L, 3L, 100L)));

        assertThat(reader.listOfStudent(3L, null)).extracting(CourseSectionEnrollmentView::id).containsExactly(10L);
    }

    @Test
    void filtersByCourseEnrollmentWhenGiven() {
        when(repository.findByStudentIdAndCourseEnrollmentIdOrderByIdAsc(3L, 100L))
                .thenReturn(List.of(enrollmentOf(10L, 3L, 100L)));

        assertThat(reader.listOfStudent(3L, 100L)).extracting(CourseSectionEnrollmentView::courseEnrollmentId)
                .containsExactly(100L);
        verify(repository).findByStudentIdAndCourseEnrollmentIdOrderByIdAsc(3L, 100L);
    }

    @Test
    void returnsAnEmptyListForAStudentWithoutSectionEnrollments() {
        when(repository.findByStudentIdOrderByIdAsc(3L)).thenReturn(List.of());

        assertThat(reader.listOfStudent(3L, null)).isEmpty();
    }

    @Test
    void mapsSectionScheduleLeadAndAssistantStatusAndAttempt() {
        CourseSubjectSectionEnrollment enrollment = enrollmentOf(10L, 3L, 100L);
        enrollment.getCourseSubjectSection().setSchedule("Lun 18:00");
        when(sectionInstructorFinder.findCourseSectionInstructors(7L)).thenReturn(List.of(
                new SectionInstructorView(1L, 11L, "Ana Adjunta", InstructorRole.ASSISTANT),
                new SectionInstructorView(2L, 12L, "Luis Titular", InstructorRole.LEAD)));
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        enrollment.setAttemptNumber(2);
        when(repository.findById(10L)).thenReturn(Optional.of(enrollment));

        CourseSectionEnrollmentView view = reader.readOfStudent(10L, 3L);

        assertThat(view.schedule()).isEqualTo("Lun 18:00");
        assertThat(view.instructorTitular()).isEqualTo("Luis Titular");
        assertThat(view.instructorAdjunto()).isEqualTo("Ana Adjunta");
        assertThat(view.status()).isEqualTo("REGULAR");
        assertThat(view.attemptNumber()).isEqualTo(2);
        assertThat(view.courseName()).isEqualTo("Curso X");
        assertThat(view.courseSubjectName()).isEqualTo("Modulo 1");
    }

    @Test
    void leavesInstructorsNullWhenTheSectionHasNone() {
        CourseSubjectSectionEnrollment enrollment = enrollmentOf(10L, 3L, 100L);
        when(repository.findById(10L)).thenReturn(Optional.of(enrollment));

        CourseSectionEnrollmentView view = reader.readOfStudent(10L, 3L);

        assertThat(view.instructorTitular()).isNull();
        assertThat(view.instructorAdjunto()).isNull();
        assertThat(view.finalScore()).isNull();
        assertThat(view.status()).isNull();
    }

    @Test
    void readingAnEnrollmentOfAnotherStudentIsNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.of(enrollmentOf(10L, 3L, 100L)));

        assertThatThrownBy(() -> reader.readOfStudent(10L, 99L)).isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void readingAnUnknownEnrollmentIsNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.readOfStudent(10L, 3L)).isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    private static CourseSubjectSectionEnrollment enrollmentOf(Long id, Long studentId, Long courseEnrollmentId) {
        Student student = new Student();
        student.setId(studentId);
        Course course = new Course();
        course.setId(5L);
        course.setName("Curso X");
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setId(courseEnrollmentId);
        courseEnrollment.setCourse(course);
        CourseSubject subject = new CourseSubject();
        subject.setId(6L);
        subject.setName("Modulo 1");
        CourseSubjectSection section = new CourseSubjectSection();
        section.setId(7L);
        section.setName("Comision A");
        section.setCourseSubject(subject);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(section);
        return enrollment;
    }
}
