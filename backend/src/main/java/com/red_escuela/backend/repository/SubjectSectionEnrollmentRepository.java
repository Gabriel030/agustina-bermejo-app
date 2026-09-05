package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectSectionEnrollmentRepository extends JpaRepository<SubjectSectionEnrollment, Long> {

    List<SubjectSectionEnrollment> findByProgramEnrollmentId(Long programEnrollmentId);

    // Listado propio del alumno (autoservicio /me), abarca todas sus carreras activas.
    List<SubjectSectionEnrollment> findByStudentId(Long studentId);

    List<SubjectSectionEnrollment> findBySubjectSectionIdAndStatusIn(Long subjectSectionId, List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long subjectSectionId, EnrollmentStatus status);

    // Correlatividades (scopeadas a la misma carrera) y chequeo de "misma materia, otra comisión".
    List<SubjectSectionEnrollment> findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
            Long programEnrollmentId, Long subjectId, List<EnrollmentStatus> statuses);

    // Resolver la fila existente al reactivate tras una baja (misma comisión, WITHDRAWN -> ENROLLED).
    Optional<SubjectSectionEnrollment> findByStudentIdAndSubjectSectionId(Long studentId, Long subjectSectionId);

    // Constancia REGULAR_STUDENT (CertificateService): el alumno tiene que estar cursando algo
    // puntual ahora mismo, no solo tener la ProgramEnrollment en ENROLLED.
    boolean existsByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<EnrollmentStatus> statuses);
}
