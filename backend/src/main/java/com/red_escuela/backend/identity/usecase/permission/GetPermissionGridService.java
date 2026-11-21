package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.specialist.PermissionGridReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPermissionGridService implements GetPermissionGridUseCase {

    private final PermissionGridReader reader;

    public GetPermissionGridService(PermissionGridReader reader) {
        this.reader = reader;
    }

    @Override
    public PermissionGridResponse get() {
        return reader.read();
    }
}
