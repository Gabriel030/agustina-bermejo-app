package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ExamBoard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamBoardRepository extends JpaRepository<ExamBoard, Long> {

    List<ExamBoard> findBySubjectIdAndTermId(Long subjectId, Long termId);

    List<ExamBoard> findByCourseSubjectIdAndTermId(Long courseSubjectId, Long termId);
}
