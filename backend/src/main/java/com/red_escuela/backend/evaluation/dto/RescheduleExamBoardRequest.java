package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RescheduleExamBoardRequest(
        @NotNull LocalDateTime examDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime registrationStart,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime registrationEnd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) @Size(max = 255) String classroom) {
}
