package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateGradeItemRequest(
        @NotNull GradeItemType type, Integer sequenceNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Double weight,
        @NotNull LocalDateTime date, @NotNull BigDecimal score,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String comments,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Long examBoardId) {
}
