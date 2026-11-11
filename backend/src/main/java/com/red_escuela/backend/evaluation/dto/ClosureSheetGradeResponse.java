package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClosureSheetGradeResponse(
        GradeItemType type,
        int sequenceNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade score,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade retakeScore,
        boolean published) {
}
