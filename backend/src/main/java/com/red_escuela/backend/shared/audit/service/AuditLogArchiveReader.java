package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogArchiveEntry;
import com.red_escuela.backend.shared.audit.AuditLogArchiveRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Component
@Transactional(readOnly = true)
public class AuditLogArchiveReader {

    private final AuditLogArchiveRepository auditLogArchiveRepository;

    public AuditLogArchiveReader(AuditLogArchiveRepository auditLogArchiveRepository) {
        this.auditLogArchiveRepository = auditLogArchiveRepository;
    }

    public Page<AuditLogArchiveEntry> findNewestFirst(AuditEntityType entityType, Collection<Long> entityIds, int limit) {
        return auditLogArchiveRepository.findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(entityType, entityIds,
                PageRequest.of(0, limit));
    }
}
