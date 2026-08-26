package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.GradeItem;
import com.red_escuela.backend.entity.GradeItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeItemRepository extends JpaRepository<GradeItem, Long> {

    List<GradeItem> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<GradeItem> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    List<GradeItem> findByExamBoardId(Long examBoardId);

    // Resolver la nota existente (misma evaluación) para corregirla en vez de duplicarla.
    Optional<GradeItem> findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);

    Optional<GradeItem> findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);
}
