package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

public record CreateExamBoardRegistrationRequest(
        @NotNull Long examBoardId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long programSectionEnrollmentId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long courseSectionEnrollmentId) {
}
