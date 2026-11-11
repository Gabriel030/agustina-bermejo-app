package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.exception.InvalidExamBoardFinalGradeEntryException;
import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Grade;

public record ExamBoardFinalGradeEntryRequest(
        @NotNull Long registrationId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade score,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean absent,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String reason) implements SelfValidatingRequest {
    public ExamBoardFinalGradeEntryRequest {
        RequestLimits.requireMaxLength("reason", reason, 500);
        boolean markedAbsent = Boolean.TRUE.equals(absent);
        if (markedAbsent == (score != null)) {
            throw new InvalidExamBoardFinalGradeEntryException("Cada fila lleva una nota o la marca de ausente, no ambas ni ninguna");
        }
    }

    public boolean markedAbsent() {
        return Boolean.TRUE.equals(absent);
    }
}
