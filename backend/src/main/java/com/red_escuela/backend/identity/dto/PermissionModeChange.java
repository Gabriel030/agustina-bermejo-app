package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.domain.PermissionModuleMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PermissionModeChange(
        @NotBlank @Size(max = 50) String role,
        @NotBlank @Size(max = 50) String module,
        @NotNull PermissionModuleMode mode) {
}
