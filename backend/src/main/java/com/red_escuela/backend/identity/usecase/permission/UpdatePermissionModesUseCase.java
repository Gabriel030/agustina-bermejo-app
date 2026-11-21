package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;

public interface UpdatePermissionModesUseCase {
    PermissionGridResponse update(UpdatePermissionModesRequest request);
}
