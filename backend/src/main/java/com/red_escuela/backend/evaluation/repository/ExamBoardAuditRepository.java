package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamBoardAuditRepository extends JpaRepository<ExamBoardAudit, Long> {

    List<ExamBoardAudit> findByExamBoardIdOrderByOccurredAtAscIdAsc(Long examBoardId);
}
