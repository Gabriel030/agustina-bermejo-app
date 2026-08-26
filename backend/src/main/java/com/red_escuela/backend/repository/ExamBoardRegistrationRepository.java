package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.ExamBoardRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardRegistrationRepository extends JpaRepository<ExamBoardRegistration, Long> {

    List<ExamBoardRegistration> findByExamBoardId(Long examBoardId);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<ExamBoardRegistration> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    // Resolver una fila WITHDRAWN previa a la misma mesa para reactivarla en vez de insertar
    // una nueva (mismo criterio que SubjectSectionEnrollmentService.inscribir con el unique
    // constraint existente) — evita chocar contra el índice único parcial de la migración V19.
    Optional<ExamBoardRegistration> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndCourseSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    // Tope de reintentos de final (decisión de producto): cuenta intentos previos no dados de
    // baja del mismo Student en la misma materia, scopeado por carrera (ProgramEnrollment) o
    // por CourseEnrollment, igual que las correlatividades de Inscripciones académicas.
    long countByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatusNot(
            Long programEnrollmentId, Long subjectId, ExamBoardRegistrationStatus status);

    long countByCourseSubjectSectionEnrollment_CourseEnrollment_IdAndCourseSubjectSectionEnrollment_CourseSubjectSection_CourseSubject_IdAndStatusNot(
            Long courseEnrollmentId, Long courseSubjectId, ExamBoardRegistrationStatus status);
}
