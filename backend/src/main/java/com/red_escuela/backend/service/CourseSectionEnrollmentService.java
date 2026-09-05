package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solo lectura + baja puntual de un módulo (ej. el alumno reprueba un módulo del curso y
 * hay que sacarlo de ese módulo sin afectar el resto). El alta es exclusivamente
 * automática desde CourseEnrollmentService.enroll — no hay alta manual por módulo
 * (decisión 15, misma coherencia que "una sola alta, todos los módulos incluidos").
 */
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
        promoteNextInWaitlist(enrollment.getCourseSubjectSection().getId());
    }

    private void promoteNextInWaitlist(Long courseSubjectSectionId) {
        courseSubjectSectionRepository.findByIdForUpdate(courseSubjectSectionId)
                .orElseThrow(() -> new SectionNotFoundException(courseSubjectSectionId));
        courseSubjectSectionEnrollmentRepository
                .findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(courseSubjectSectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(next -> {
                    next.setStatus(EnrollmentStatus.ENROLLED);
                    courseSubjectSectionEnrollmentRepository.save(next);
                });
    }

    private CourseSubjectSectionEnrollment findOrThrow(Long id) {
        return courseSubjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
