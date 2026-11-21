package com.red_escuela.backend.identity.dto;

import java.util.List;

public record PermissionModuleResponse(String module, List<PermissionResponse> permissions) {
}
