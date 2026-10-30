package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionEnrollmentFinder {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES = List.of(
            EnrollmentStatus.ENROLLED,
            EnrollmentStatus.IN_PROGRESS);

    private static final List<EnrollmentStatus> MEMBER_STATUSES = List.of(
            EnrollmentStatus.ENROLLED,
            EnrollmentStatus.IN_PROGRESS,
            EnrollmentStatus.REGULAR,
            EnrollmentStatus.APPROVED,
            EnrollmentStatus.FAILED);

    private final SubjectSectionEnrollmentRepository programEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;

    public SectionEnrollmentFinder(SubjectSectionEnrollmentRepository programEnrollmentRepository,
                                   CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public SubjectSectionEnrollment findProgramEnrollment(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    public CourseSubjectSectionEnrollment findCourseEnrollment(Long id) {
        return courseEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    public Optional<SubjectSectionEnrollment> findProgramEnrollmentIfPresent(Long id) {
        return programEnrollmentRepository.findById(id);
    }

    public Optional<CourseSubjectSectionEnrollment> findCourseEnrollmentIfPresent(Long id) {
        return courseEnrollmentRepository.findById(id);
    }

    public SubjectSectionEnrollment requireProgramEnrollmentOf(Long id, Long studentId) {
        SubjectSectionEnrollment enrollment = findProgramEnrollment(id);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new SectionEnrollmentNotFoundException(id);
        }
        return enrollment;
    }

    public CourseSubjectSectionEnrollment requireCourseEnrollmentOf(Long id, Long studentId) {
        CourseSubjectSectionEnrollment enrollment = findCourseEnrollment(id);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new SectionEnrollmentNotFoundException(id);
        }
        return enrollment;
    }

    public List<SubjectSectionEnrollment> findActiveInProgramSection(Long sectionId) {
        return programEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_STATUSES);
    }

    public List<CourseSubjectSectionEnrollment> findActiveInCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_STATUSES);
    }

    public List<SubjectSectionEnrollment> findActiveWithStudentInProgramSection(Long sectionId) {
        return programEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, ACTIVE_STATUSES);
    }

    public List<CourseSubjectSectionEnrollment> findActiveWithStudentInCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, ACTIVE_STATUSES);
    }

    public List<SubjectSectionEnrollment> findMembersOfProgramSection(Long sectionId) {
        return programEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }

    public List<CourseSubjectSectionEnrollment> findMembersOfCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }
}
