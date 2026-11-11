package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionClosureAuditRepository extends JpaRepository<CommissionClosureAudit, Long> {

    List<CommissionClosureAudit> findByClosureIdOrderByOccurredAtAscIdAsc(Long closureId);
}
