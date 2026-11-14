package com.red_escuela.backend.identity.usecase.audit;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListAuditLogUseCase {
    PageResponse<AuditLogView> list(AuditAction action, AuditEntityType entityType, Long entityId, int page, int size);
}
