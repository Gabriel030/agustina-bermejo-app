package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GradeGridColumnResponse(
        GradeItemType type,
        Integer sequenceNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDate date,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean published,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime publishedAt) {
}
