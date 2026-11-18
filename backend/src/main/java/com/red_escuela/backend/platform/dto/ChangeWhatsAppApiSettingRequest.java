package com.red_escuela.backend.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeWhatsAppApiSettingRequest(
        @NotNull Boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 500) String reason) {
}
