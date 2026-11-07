package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record UpdateSectionEvaluationConfigRequest(
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer tpCount,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate tp1DueDate, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate tp2DueDate, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate tp3DueDate,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate exam1Date, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate exam2Date, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate exam3Date, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate exam4Date,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate finalCall1Date, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate finalCall2Date) {
}
