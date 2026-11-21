package com.red_escuela.backend.identity.dto;

import java.util.List;

public record PermissionGridResponse(
        List<RolePermissionsResponse> roles,
        List<PermissionModuleModeResponse> modes,
        boolean editable) {
}
