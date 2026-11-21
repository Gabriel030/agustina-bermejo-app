package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetPermissionGridService implements GetPermissionGridUseCase {

    @Override
    public PermissionGridResponse get() {
        return new PermissionGridResponse(List.of(), List.of(), false);
    }
}
