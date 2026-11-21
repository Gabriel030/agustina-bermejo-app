package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;
import com.red_escuela.backend.identity.specialist.PermissionGridReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdatePermissionModesService implements UpdatePermissionModesUseCase {

    private final PermissionGridReader reader;

    public UpdatePermissionModesService(PermissionGridReader reader) {
        this.reader = reader;
    }

    @Override
    public PermissionGridResponse update(UpdatePermissionModesRequest request) {
        return reader.read();
    }
}
