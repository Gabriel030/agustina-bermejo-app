package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdatePermissionModesService implements UpdatePermissionModesUseCase {

    private final GetPermissionGridUseCase getPermissionGrid;

    public UpdatePermissionModesService(GetPermissionGridUseCase getPermissionGrid) {
        this.getPermissionGrid = getPermissionGrid;
    }

    @Override
    public PermissionGridResponse update(UpdatePermissionModesRequest request) {
        return getPermissionGrid.get();
    }
}
