package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEvaluationSchemeRequest(
        @NotBlank String name,
        @NotNull Integer examCountExpected,
        @NotNull Boolean hasFinal,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Double finalWeight,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) GradeAggregationMethod aggregationMethod,
        @NotNull BigDecimal minRegularGrade,
        @NotNull BigDecimal minApprovalGrade,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) BigDecimal promotionMinGrade,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String notes,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer recoveryQuota) {
}
