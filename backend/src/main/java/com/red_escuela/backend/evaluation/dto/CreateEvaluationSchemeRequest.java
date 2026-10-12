package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;

import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEvaluationSchemeRequest(
        @NotBlank String name,
        @NotNull Integer examCountExpected,
        @NotNull Boolean hasFinal,
        Double finalWeight,
        GradeAggregationMethod aggregationMethod,
        @NotNull BigDecimal minRegularGrade,
        @NotNull BigDecimal minApprovalGrade,
        BigDecimal promotionMinGrade,
        String notes) {
}
