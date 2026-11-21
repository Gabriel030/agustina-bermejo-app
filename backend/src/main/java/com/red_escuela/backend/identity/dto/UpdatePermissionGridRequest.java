package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePermissionGridRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Size(max = 200) List<@Valid PermissionGridChange> changes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean sensitiveConfirmed) {
}
