package com.red_escuela.backend.identity.dto;

import java.util.List;

public record RolePermissionsResponse(String role, List<String> permissionCodes) {
}
