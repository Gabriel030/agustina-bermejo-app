package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;

public record EvaluationSchemeResponse(
        Long id, String name, Integer examCountExpected, Boolean hasFinal, Double finalWeight,
        String aggregationMethod, Double minRegularGrade, Double minApprovalGrade,
        Double promotionMinGrade, String notes, boolean inUse) {

    public static EvaluationSchemeResponse from(EvaluationScheme scheme, boolean inUse) {
        return new EvaluationSchemeResponse(
                scheme.getId(), scheme.getName(), scheme.getExamCountExpected(), scheme.getHasFinal(),
                scheme.getFinalWeight(),
                scheme.getAggregationMethod() == null ? null : scheme.getAggregationMethod().name(),
                scheme.getMinRegularGrade(), scheme.getMinApprovalGrade(), scheme.getPromotionMinGrade(),
                scheme.getNotes(), inUse);
    }
}
