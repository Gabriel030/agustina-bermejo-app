package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListPlatformAuditLogUseCase {
    PageResponse<AuditLogView> list(AuditLogFilter filter, String actorName, int page, int size);
}
