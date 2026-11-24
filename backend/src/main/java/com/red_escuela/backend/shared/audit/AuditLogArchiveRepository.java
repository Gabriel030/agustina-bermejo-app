package com.red_escuela.backend.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AuditLogArchiveRepository extends JpaRepository<AuditLogArchiveEntry, Long> {

    Page<AuditLogArchiveEntry> findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(String entityType,
                                                                                        Collection<Long> entityIds,
                                                                                        Pageable pageable);
}
