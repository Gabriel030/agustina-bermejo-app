package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<Attendance> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    // Resolver la fila existente para no duplicar al cargar asistencia dos veces el mismo día.
    Optional<Attendance> findByProgramSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    Optional<Attendance> findByCourseSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    long countByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    long countByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    long countByProgramSubjectSectionEnrollmentIdAndStatus(Long enrollmentId, AttendanceStatus status);

    long countByCourseSubjectSectionEnrollmentIdAndStatus(Long enrollmentId, AttendanceStatus status);

    long countByProgramSubjectSectionEnrollmentIdAndStatusIn(Long enrollmentId, List<AttendanceStatus> statuses);

    long countByCourseSubjectSectionEnrollmentIdAndStatusIn(Long enrollmentId, List<AttendanceStatus> statuses);

    // Historial/reporte de ausencias: todas las asistencias de la comisión completa, sin N+1
    // por alumno.
    List<Attendance> findByProgramSubjectSectionEnrollment_SubjectSection_Id(Long subjectSectionId);

    List<Attendance> findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(Long courseSubjectSectionId);
}
