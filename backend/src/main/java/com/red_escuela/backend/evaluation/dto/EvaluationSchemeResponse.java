package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.Grade;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;

public record EvaluationSchemeResponse(
        Long id, String name, Integer examCountExpected, Boolean hasFinal, Double finalWeight,
        String aggregationMethod, BigDecimal minRegularGrade, BigDecimal minApprovalGrade,
        BigDecimal promotionMinGrade, String notes, boolean inUse) {

    public static EvaluationSchemeResponse from(EvaluationScheme scheme, boolean inUse) {
        return new EvaluationSchemeResponse(
                scheme.getId(), scheme.getName(), scheme.getExamCountExpected(), scheme.getHasFinal(),
                scheme.getFinalWeight(),
                scheme.getAggregationMethod() == null ? null : scheme.getAggregationMethod().name(),
                Grade.valueOrNull(scheme.getMinRegularGrade()), Grade.valueOrNull(scheme.getMinApprovalGrade()),
                Grade.valueOrNull(scheme.getPromotionMinGrade()),
                scheme.getNotes(), inUse);
    }
}
