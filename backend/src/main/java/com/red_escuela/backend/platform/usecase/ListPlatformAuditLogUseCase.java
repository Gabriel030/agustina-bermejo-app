package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListPlatformAuditLogUseCase {
    PageResponse<AuditLogView> list(AuditAction action, AuditEntityType entityType, Long entityId, int page, int size);
}
