package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionGridRequest;

public interface UpdatePermissionGridUseCase {
    PermissionGridResponse update(UpdatePermissionGridRequest request);
}
