package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ClosureSheetRequirementsResponse(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean coursework,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean correlatives,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean attendance,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean grade) {
}
