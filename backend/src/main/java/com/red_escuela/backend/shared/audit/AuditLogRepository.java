package com.red_escuela.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    @Query("""
            select e from AuditLogEntry e
            where (:action is null or e.action = :action)
              and (:entityType is null or e.entityType = :entityType)
              and (:entityId is null or e.entityId = :entityId)
            order by e.occurredAt desc, e.id desc
            """)
    Page<AuditLogEntry> search(@Param("action") AuditAction action,
                               @Param("entityType") AuditEntityType entityType,
                               @Param("entityId") Long entityId,
                               Pageable pageable);

    Page<AuditLogEntry> findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(AuditEntityType entityType,
                                                                                 java.util.Collection<Long> entityIds,
                                                                                 Pageable pageable);
}
