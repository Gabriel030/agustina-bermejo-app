package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardAudit;
import com.red_escuela.backend.evaluation.entity.ExamBoardAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardAuditRepository extends JpaRepository<ExamBoardAudit, Long> {

    List<ExamBoardAudit> findByExamBoardIdOrderByOccurredAtAscIdAsc(Long examBoardId);

    @Query("select max(a.occurredAt) from ExamBoardAudit a where a.examBoardId = :examBoardId and a.action = :action")
    Optional<LocalDateTime> findLastOccurredAt(@Param("examBoardId") Long examBoardId, @Param("action") ExamBoardAuditAction action);
}
