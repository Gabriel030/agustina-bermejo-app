package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEvaluationSchemeRequest(
        @NotBlank String name,
        @NotNull Integer examCountExpected,
        @NotNull Boolean hasFinal,
        Double finalWeight,
        GradeAggregationMethod aggregationMethod,
        @NotNull Double minRegularGrade,
        @NotNull Double minApprovalGrade,
        Double promotionMinGrade,
        String notes) {
}
