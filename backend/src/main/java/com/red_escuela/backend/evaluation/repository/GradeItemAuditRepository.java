package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeItemAuditRepository extends JpaRepository<GradeItemAudit, Long> {

    List<GradeItemAudit> findByGradeItemIdOrderByChangedAtDescIdDesc(Long gradeItemId);
}
