package com.red_escuela.backend.identity.dto;

import java.util.List;

public record PermissionCatalogResponse(List<PermissionModuleResponse> modules) {
}
