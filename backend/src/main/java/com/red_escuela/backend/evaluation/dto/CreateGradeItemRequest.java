package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
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
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Long examBoardId) implements SelfValidatingRequest {
    public CreateGradeItemRequest {
        RequestLimits.requireMaxLength("comments", comments, 255);
        RequestLimits.requireAtMost("sequenceNumber", sequenceNumber, 1000);
    }
}
