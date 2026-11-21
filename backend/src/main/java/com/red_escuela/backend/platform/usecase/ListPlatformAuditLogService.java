package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.specialist.PlatformAuditActorNameResolver;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.audit.ActorNameTerm;
import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.audit.service.AuditLogReader;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListPlatformAuditLogService implements ListPlatformAuditLogUseCase {

    private final AuditLogReader auditLogReader;
    private final PlatformAuditActorNameResolver platformAuditActorNameResolver;

    public ListPlatformAuditLogService(AuditLogReader auditLogReader, PlatformAuditActorNameResolver platformAuditActorNameResolver) {
        this.auditLogReader = auditLogReader;
        this.platformAuditActorNameResolver = platformAuditActorNameResolver;
    }

    @Override
    public PageResponse<AuditLogView> list(AuditLogFilter filter, String actorName, int page, int size) {
        AuditLogFilter effectiveFilter = ActorNameTerm.parse(actorName)
                .map(term -> filter.restrictedToActors(platformAuditActorNameResolver.findPersonIdsByName(term, ActorNameTerm.MAX_MATCHES)))
                .orElse(filter);
        return auditLogReader.search(effectiveFilter, page, size, platformAuditActorNameResolver::resolve);
    }
}
