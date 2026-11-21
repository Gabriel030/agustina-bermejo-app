package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record PermissionGridChange(
        @NotBlank String role,
        @NotBlank String permissionCode,
        boolean granted) {
}
