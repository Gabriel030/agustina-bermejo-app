package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseSectionEnrollmentService {
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;

    public CourseSectionEnrollmentService(
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository) {
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
    }

    public List<CourseSubjectSectionEnrollment> listByEnrollment(Long courseEnrollmentId) {
        return courseSubjectSectionEnrollmentRepository.findByCourseEnrollmentId(courseEnrollmentId);
    }

    @Transactional
    public void withdraw(Long id) {
        CourseSubjectSectionEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now());
        courseSubjectSectionEnrollmentRepository.save(enrollment);
        promoteNextFromWaitlist(enrollment.getCourseSubjectSection().getId());
    }

    private void promoteNextFromWaitlist(Long courseSubjectSectionId) {
        courseSubjectSectionRepository.findByIdForUpdate(courseSubjectSectionId)
                .orElseThrow(() -> new SectionNotFoundException(courseSubjectSectionId));
        courseSubjectSectionEnrollmentRepository
                .findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(courseSubjectSectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseSubjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    private CourseSubjectSectionEnrollment findOrThrow(Long id) {
        return courseSubjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
