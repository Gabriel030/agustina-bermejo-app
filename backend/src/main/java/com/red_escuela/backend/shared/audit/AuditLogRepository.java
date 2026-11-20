package com.red_escuela.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

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
    int archiveOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    Page<AuditLogEntry> findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(AuditEntityType entityType,
                                                                                 java.util.Collection<Long> entityIds,
                                                                                 Pageable pageable);
}
