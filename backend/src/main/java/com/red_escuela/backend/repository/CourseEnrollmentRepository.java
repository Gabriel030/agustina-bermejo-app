package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    List<CourseEnrollment> findByStudentId(Long studentId);

    List<CourseEnrollment> findByStudentIdAndCourseIdAndStatusIn(
            Long studentId, Long courseId, List<EnrollmentStatus> statuses);

    // Motor de facturación recurrente (Épica 8 Fase 2): solo ENROLLED recibe
    // cuotas nuevas.
    List<CourseEnrollment> findByStatus(EnrollmentStatus status);

    // Cupo de Course.capacity: cuántos ocupan lugar hoy (ENROLLED cuenta contra el cupo,
    // WAITLISTED no).
    List<CourseEnrollment> findByCourseIdAndStatusIn(Long courseId, List<EnrollmentStatus> statuses);

    // Promoción FIFO de la lista de espera del curso — mismo criterio de orden que las
    // comisiones (findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc), acá con
    // enrollmentDate porque CourseEnrollment no tiene un campo enrolledAt propio.
    Optional<CourseEnrollment> findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(Long courseId, EnrollmentStatus status);

    // CourseEnrollmentWaitlistExpiryJob: todos los que siguen esperando cupo de un curso puntual.
    List<CourseEnrollment> findByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    // EnrollmentBillingComplianceSweepService (Fase 6.3): paginado OFFSET normal, mismo criterio
    // que ProgramEnrollmentRepository.findByStatusInOrderByIdAsc.
    List<CourseEnrollment> findByStatusOrderByIdAsc(EnrollmentStatus status, Pageable pageable);
}
