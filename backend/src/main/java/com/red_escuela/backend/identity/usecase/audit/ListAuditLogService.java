package com.red_escuela.backend.identity.usecase.audit;

import com.red_escuela.backend.identity.service.AuditActorNameResolver;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.audit.service.AuditLogReader;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListAuditLogService implements ListAuditLogUseCase {

    private final AuditLogReader auditLogReader;
    private final AuditActorNameResolver auditActorNameResolver;

    public ListAuditLogService(AuditLogReader auditLogReader, AuditActorNameResolver auditActorNameResolver) {
        this.auditLogReader = auditLogReader;
        this.auditActorNameResolver = auditActorNameResolver;
    }

    @Override
    public PageResponse<AuditLogView> list(AuditLogFilter filter, int page, int size) {
        return auditLogReader.search(filter, page, size, auditActorNameResolver::resolve);
    }
}
