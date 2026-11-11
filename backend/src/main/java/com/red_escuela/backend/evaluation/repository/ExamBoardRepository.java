package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardRepository extends JpaRepository<ExamBoard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ExamBoard b where b.id = :id")
    Optional<ExamBoard> findByIdForUpdate(@Param("id") Long id);

    List<ExamBoard> findBySubjectIdAndTermId(Long subjectId, Long termId);

    List<ExamBoard> findByCourseSubjectIdAndTermId(Long courseSubjectId, Long termId);

    List<ExamBoard> findBySubjectIdAndStatus(Long subjectId, ExamBoardStatus status);

    List<ExamBoard> findAllByOrderByExamDateDesc();

    List<ExamBoard> findByStatusOrderByExamDateDesc(ExamBoardStatus status);
}
