package com.red_escuela.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    Page<AuditLogEntry> findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(AuditEntityType entityType,
                                                                                 java.util.Collection<Long> entityIds,
                                                                                 Pageable pageable);
}
