package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateAcademicOfferingCycleRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long programId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long courseId,
        @NotNull Long termId
) {}
