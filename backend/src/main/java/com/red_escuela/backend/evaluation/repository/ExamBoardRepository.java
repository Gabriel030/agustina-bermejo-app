package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamBoardRepository extends JpaRepository<ExamBoard, Long> {

    List<ExamBoard> findBySubjectIdAndTermId(Long subjectId, Long termId);

    List<ExamBoard> findByCourseSubjectIdAndTermId(Long courseSubjectId, Long termId);

    List<ExamBoard> findBySubjectIdAndStatus(Long subjectId, ExamBoardStatus status);

    List<ExamBoard> findAllByOrderByExamDateDesc();

    List<ExamBoard> findByStatusOrderByExamDateDesc(ExamBoardStatus status);
}
