package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.identity.specialist.PermissionDiscrepancyReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListPermissionDiscrepanciesService implements ListPermissionDiscrepanciesUseCase {

    private final PermissionDiscrepancyReader reader;

    public ListPermissionDiscrepanciesService(PermissionDiscrepancyReader reader) {
        this.reader = reader;
    }

    @Override
    public PageResponse<PermissionDiscrepancyResponse> list(int page, int size) {
        return reader.read(page, size);
    }
}
