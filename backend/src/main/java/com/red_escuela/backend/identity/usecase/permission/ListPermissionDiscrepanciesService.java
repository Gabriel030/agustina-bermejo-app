package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListPermissionDiscrepanciesService implements ListPermissionDiscrepanciesUseCase {

    @Override
    public PageResponse<PermissionDiscrepancyResponse> list(int page, int size) {
        throw new UnsupportedOperationException();
    }
}
