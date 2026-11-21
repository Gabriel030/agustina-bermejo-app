package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListPermissionDiscrepanciesUseCase {
    PageResponse<PermissionDiscrepancyResponse> list(String module, int page, int size);
}
