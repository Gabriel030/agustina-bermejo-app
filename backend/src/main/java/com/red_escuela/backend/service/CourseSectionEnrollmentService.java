package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solo lectura + baja puntual de un módulo (ej. el alumno reprueba un módulo del curso y
 * hay que sacarlo de ese módulo sin afectar el resto). El alta es exclusivamente
 * automática desde CourseEnrollmentService.matricular — no hay alta manual por módulo
 * (decisión 15, misma coherencia que "una sola alta, todos los módulos incluidos").
 */
@Service
public class CourseSectionEnrollmentService {

    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;

    public CourseSectionEnrollmentService(CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    public List<CourseSubjectSectionEnrollment> listarPorMatriculacion(Long courseEnrollmentId) {
        return courseSubjectSectionEnrollmentRepository.findByCourseEnrollmentId(courseEnrollmentId);
    }

    @Transactional
    public void darDeBaja(Long id) {
        CourseSubjectSectionEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now());
        courseSubjectSectionEnrollmentRepository.save(enrollment);
        promoverSiguienteEnListaDeEspera(enrollment.getCourseSubjectSection().getId());
    }

    private void promoverSiguienteEnListaDeEspera(Long courseSubjectSectionId) {
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
