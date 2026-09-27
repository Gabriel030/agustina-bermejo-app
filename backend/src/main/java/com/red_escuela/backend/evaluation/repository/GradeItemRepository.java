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

    List<GradeItem> findByProgramSubjectSectionEnrollmentIdIn(List<Long> enrollmentIds);

    List<GradeItem> findByCourseSubjectSectionEnrollmentIdIn(List<Long> enrollmentIds);

    List<GradeItem> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    List<GradeItem> findByExamBoardId(Long examBoardId);

    List<GradeItem> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(Long examBoardId, Long enrollmentId);

    Optional<GradeItem> findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);

    Optional<GradeItem> findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
            Long enrollmentId, GradeItemType type, Integer sequenceNumber);
}
