package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.domain.PermissionModuleMode;

public record PermissionModuleModeResponse(String role, String module, PermissionModuleMode mode) {
}
