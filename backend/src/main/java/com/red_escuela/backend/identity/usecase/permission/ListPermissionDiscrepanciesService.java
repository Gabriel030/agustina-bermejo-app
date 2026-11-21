package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPermissionDiscrepanciesService implements ListPermissionDiscrepanciesUseCase {

    @Override
    public PageResponse<PermissionDiscrepancyResponse> list(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0);
    }
}
