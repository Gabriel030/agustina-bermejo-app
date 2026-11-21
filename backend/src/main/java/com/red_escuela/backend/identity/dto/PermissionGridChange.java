package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionGridChange(
        @NotBlank @Size(max = 50) String role,
        @NotBlank @Size(max = 100) String permissionCode,
        boolean granted) {
}
