package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClosureSheetSchemeResponse(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade minRegularGrade,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade minApprovalGrade,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade promotionMinGrade,
        int examCountExpected,
        int tpCountExpected,
        int retakeQuota,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Percentage maxAbsenceRate) {
}
