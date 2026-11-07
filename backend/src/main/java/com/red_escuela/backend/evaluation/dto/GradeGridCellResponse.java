package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.shared.domain.Grade;
import java.time.LocalDateTime;

public record GradeGridCellResponse(
        Long gradeItemId,
        GradeItemType type,
        Integer sequenceNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade score,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime date,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String comments,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean published,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean corrected) {
}
