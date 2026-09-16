package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeItemRepository extends JpaRepository<GradeItem, Long> {

    List<GradeItem> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<GradeItem> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    List<GradeItem> findByExamBoardId(Long examBoardId);

    // Nota del alumno en UNA mesa puntual — ExamBoardRegistration.status nunca pasa a GRADED en
    // el código actual, así que "ya rindió y sacó X" se resuelve cruzando acá, no por status.
    List<GradeItem> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(Long examBoardId, Long enrollmentId);

    // Resolver la nota existente (misma evaluación) para corregirla en vez de duplicarla.
    Optional<GradeItem> findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);

    Optional<GradeItem> findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);
}
