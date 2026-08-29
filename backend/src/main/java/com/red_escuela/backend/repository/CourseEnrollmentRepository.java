package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    List<CourseEnrollment> findByStudentId(Long studentId);

    List<CourseEnrollment> findByStudentIdAndCourseIdAndStatusIn(
            Long studentId, Long courseId, List<EnrollmentStatus> statuses);

    // Motor de facturación recurrente (Épica 8 Fase 2): solo ENROLLED recibe
    // cuotas nuevas.
    List<CourseEnrollment> findByStatus(EnrollmentStatus status);
}
