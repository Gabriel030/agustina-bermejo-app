package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorView;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class CourseSectionEnrollmentReader {

    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    private final SectionInstructorFinder sectionInstructorFinder;

    public CourseSectionEnrollmentReader(CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository,
                                         SectionInstructorFinder sectionInstructorFinder) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.sectionInstructorFinder = sectionInstructorFinder;
    }

    public List<CourseSectionEnrollmentView> listOfStudent(Long studentId, Long courseEnrollmentId) {
        List<CourseSubjectSectionEnrollment> enrollments = courseEnrollmentId == null
                ? courseEnrollmentRepository.findByStudentIdOrderByIdAsc(studentId)
                : courseEnrollmentRepository.findByStudentIdAndCourseEnrollmentIdOrderByIdAsc(studentId, courseEnrollmentId);
        return enrollments.stream().map(this::viewOf).toList();
    }

    public CourseSectionEnrollmentView readOfStudent(Long id, Long studentId) {
        return courseEnrollmentRepository.findById(id)
                .filter(enrollment -> enrollment.getStudent().getId().equals(studentId))
                .map(this::viewOf)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    private CourseSectionEnrollmentView viewOf(CourseSubjectSectionEnrollment enrollment) {
        CourseSubjectSection section = enrollment.getCourseSubjectSection();
        List<SectionInstructorView> instructors = sectionInstructorFinder.findCourseSectionInstructors(section.getId());
        return new CourseSectionEnrollmentView(
                enrollment.getId(),
                enrollment.getCourseEnrollment().getId(),
                enrollment.getCourseEnrollment().getCourse().getId(),
                enrollment.getCourseEnrollment().getCourse().getName(),
                section.getId(),
                section.getName(),
                section.getCourseSubject().getId(),
                section.getCourseSubject().getName(),
                enrollment.getStatus() == null ? null : enrollment.getStatus().name(),
                enrollment.getAttemptNumber(),
                enrollment.getFinalScore(),
                enrollment.getFinalGradeType() == null ? null : enrollment.getFinalGradeType().name(),
                enrollment.getEnrolledAt(),
                enrollment.getDroppedAt(),
                section.getSchedule(),
                instructorNameWithRole(instructors, InstructorRole.LEAD),
                instructorNameWithRole(instructors, InstructorRole.ASSISTANT));
    }

    private static String instructorNameWithRole(List<SectionInstructorView> instructors, InstructorRole role) {
        return instructors.stream()
                .filter(instructor -> instructor.role() == role)
                .findFirst()
                .map(SectionInstructorView::fullName)
                .orElse(null);
    }
}
