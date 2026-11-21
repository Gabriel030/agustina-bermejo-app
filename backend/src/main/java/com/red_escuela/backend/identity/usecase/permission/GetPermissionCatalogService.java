package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionCatalogResponse;
import com.red_escuela.backend.identity.specialist.PermissionCatalogReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPermissionCatalogService implements GetPermissionCatalogUseCase {

    private final PermissionCatalogReader reader;

    public GetPermissionCatalogService(PermissionCatalogReader reader) {
        this.reader = reader;
    }

    @Override
    public PermissionCatalogResponse get() {
        return reader.read();
    }
}
