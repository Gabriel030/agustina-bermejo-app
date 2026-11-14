package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeSectionRequest(
        @NotNull Long newSectionId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Motivo del cambio (10 a 500 caracteres); obligatorio cuando lo registra el personal") String reason) {

    public ChangeSectionRequest(Long newSectionId) {
        this(newSectionId, null);
    }
}
