package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CorrectGradeItemRequest(
        @NotNull Grade newScore,
        @NotNull String reason,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) @PositiveOrZero Long expectedVersion) {
}
