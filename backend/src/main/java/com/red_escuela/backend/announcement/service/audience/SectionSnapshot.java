package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.evaluation.api.EvaluationSchemeSummary;

import java.util.List;

public record SectionSnapshot(List<StudentSnapshot> students, EvaluationSchemeSummary evaluationScheme) {

    public boolean hasEvaluationScheme() {
        return evaluationScheme != null;
    }

    public boolean hasFinal() {
        return evaluationScheme != null && Boolean.TRUE.equals(evaluationScheme.hasFinal());
    }

    public int examCount() {
        return evaluationScheme == null || evaluationScheme.examCountExpected() == null
                ? 0 : evaluationScheme.examCountExpected();
    }

    public int tpCount() {
        return evaluationScheme == null || evaluationScheme.tpCountExpected() == null
                ? 0 : evaluationScheme.tpCountExpected();
    }

    public double minApprovalGrade() {
        return evaluationScheme == null || evaluationScheme.minApprovalGrade() == null
                ? 4.0 : evaluationScheme.minApprovalGrade();
    }
}
