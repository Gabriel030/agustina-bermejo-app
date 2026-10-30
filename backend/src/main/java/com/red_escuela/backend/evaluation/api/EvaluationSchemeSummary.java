package com.red_escuela.backend.evaluation.api;

public record EvaluationSchemeSummary(
        Integer examCountExpected,
        Integer tpCountExpected,
        Boolean hasFinal,
        Double minApprovalGrade) {
}
