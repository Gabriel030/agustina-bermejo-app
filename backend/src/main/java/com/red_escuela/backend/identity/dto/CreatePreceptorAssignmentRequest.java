package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreatePreceptorAssignmentRequest(
        @NotNull Long personId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long subjectSectionId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long courseSectionId) {
}
