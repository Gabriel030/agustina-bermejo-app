package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionCatalogResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetPermissionCatalogService implements GetPermissionCatalogUseCase {

    @Override
    public PermissionCatalogResponse get() {
        return new PermissionCatalogResponse(List.of());
    }
}
