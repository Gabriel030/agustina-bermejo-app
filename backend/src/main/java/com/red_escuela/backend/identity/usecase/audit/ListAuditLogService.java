package com.red_escuela.backend.identity.usecase.audit;

import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.service.AuditLogReader;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListAuditLogService implements ListAuditLogUseCase {

    private final AuditLogReader auditLogReader;

    public ListAuditLogService(AuditLogReader auditLogReader) {
        this.auditLogReader = auditLogReader;
    }

    @Override
    public PageResponse<AuditLogView> list(AuditAction action, AuditEntityType entityType, Long entityId,
                                           int page, int size) {
        return auditLogReader.search(action, entityType, entityId, page, size);
    }
}
