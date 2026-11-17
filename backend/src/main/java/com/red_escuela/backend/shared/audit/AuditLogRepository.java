package com.red_escuela.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

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

    @Modifying
    @Query(nativeQuery = true, value = """
            with moved as (
                delete from audit_log
                where id in (select id from audit_log where occurred_at < :cutoff order by id limit :batchSize)
                returning id, occurred_at, actor_person_id, actor_role, action, entity_type, entity_id, before_value, after_value, reason
            )
            insert into audit_log_archive (id, occurred_at, actor_person_id, actor_role, action, entity_type, entity_id, before_value, after_value, reason)
            select id, occurred_at, actor_person_id, actor_role, action, entity_type, entity_id, before_value, after_value, reason from moved
            """)
    int archiveOlderThan(@Param("cutoff") java.time.LocalDateTime cutoff, @Param("batchSize") int batchSize);

    Page<AuditLogEntry> findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(AuditEntityType entityType,
                                                                                 java.util.Collection<Long> entityIds,
                                                                                 Pageable pageable);
}
