package com.red_escuela.backend.shared.audit.service;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.audit.AuditLogView;

import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AuditLogReader {

    private final AuditLogRepository auditLogRepository;

    public AuditLogReader(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public PageResponse<AuditLogView> search(AuditAction action, AuditEntityType entityType, Long entityId,
                                             int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<AuditLogEntry> result = auditLogRepository.search(action, entityType, entityId,
                PageRequest.of(boundedPage, boundedSize));
        return new PageResponse<>(result.getContent().stream().map(AuditLogView::from).toList(),
                boundedPage, boundedSize, result.getTotalElements());
    }
}
