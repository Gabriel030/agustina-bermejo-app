package com.red_escuela.backend.identity.usecase.audit;

import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListAuditLogUseCase {
    PageResponse<AuditLogView> list(AuditLogFilter filter, int page, int size);
}
