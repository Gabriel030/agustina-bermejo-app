package com.red_escuela.backend.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePermissionModesRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Size(max = 200) List<@Valid PermissionModeChange> changes) {
}
