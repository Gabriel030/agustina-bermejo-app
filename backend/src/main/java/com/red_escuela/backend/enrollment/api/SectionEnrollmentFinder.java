package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
    private final com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository billingProgramEnrollmentRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public SectionEnrollmentFinder(SubjectSectionEnrollmentRepository programEnrollmentRepository,
                                   CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository,
                                   com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository billingProgramEnrollmentRepository,
                                   jakarta.persistence.EntityManager entityManager) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.billingProgramEnrollmentRepository = billingProgramEnrollmentRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockBillingProgramEnrollment(Long programEnrollmentId) {
        billingProgramEnrollmentRepository.findByIdForUpdate(programEnrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(programEnrollmentId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SubjectSectionEnrollment refreshProgramEnrollment(Long id) {
        SubjectSectionEnrollment enrollment = findProgramEnrollment(id);
        entityManager.refresh(enrollment);
        return enrollment;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CourseSubjectSectionEnrollment refreshCourseEnrollment(Long id) {
        CourseSubjectSectionEnrollment enrollment = findCourseEnrollment(id);
        entityManager.refresh(enrollment);
        return enrollment;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProgramEnrollment(Long id) {
        programEnrollmentRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCourseEnrollment(Long id) {
        courseEnrollmentRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    public SubjectSectionEnrollment findProgramEnrollment(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }

    public java.util.List<SubjectSectionEnrollment> findProgramEnrollmentsWithSection(java.util.Collection<Long> ids) {
        return programEnrollmentRepository.findWithSectionByIdIn(ids);
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

    public void requireActiveInProgramSection(Long sectionId, java.util.Collection<Long> enrollmentIds) {
        java.util.Set<Long> activeIds = findActiveInProgramSection(sectionId).stream()
                .map(SubjectSectionEnrollment::getId).collect(java.util.stream.Collectors.toSet());
        enrollmentIds.stream().filter(id -> !activeIds.contains(id)).findFirst()
                .ifPresent(id -> {
                    throw new SectionEnrollmentNotFoundException(id);
                });
    }

    public void requireActiveInCourseSection(Long sectionId, java.util.Collection<Long> enrollmentIds) {
        java.util.Set<Long> activeIds = findActiveInCourseSection(sectionId).stream()
                .map(CourseSubjectSectionEnrollment::getId).collect(java.util.stream.Collectors.toSet());
        enrollmentIds.stream().filter(id -> !activeIds.contains(id)).findFirst()
                .ifPresent(id -> {
                    throw new SectionEnrollmentNotFoundException(id);
                });
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

    public List<SubjectSectionEnrollment> findMembersWithStudentInProgramSection(Long sectionId) {
        return programEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }

    public List<CourseSubjectSectionEnrollment> findMembersWithStudentInCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }

    public List<SubjectSectionEnrollment> findWithdrawnWithStudentInProgramSection(Long sectionId) {
        return programEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, List.of(EnrollmentStatus.WITHDRAWN));
    }

    public List<CourseSubjectSectionEnrollment> findWithdrawnWithStudentInCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findWithStudentBySectionIdAndStatusIn(sectionId, List.of(EnrollmentStatus.WITHDRAWN));
    }

    public List<SubjectSectionEnrollment> findMembersOfProgramSection(Long sectionId) {
        return programEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }

    public List<CourseSubjectSectionEnrollment> findMembersOfCourseSection(Long sectionId) {
        return courseEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, MEMBER_STATUSES);
    }
}
