package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;

import java.util.List;

public record SectionSnapshot(List<StudentSnapshot> students, EvaluationScheme evaluationScheme) {

    public boolean hasEvaluationScheme() {
        return evaluationScheme != null;
    }

    public int examCount() {
        return evaluationScheme == null || evaluationScheme.getExamCountExpected() == null
                ? 0 : evaluationScheme.getExamCountExpected();
    }

    public int tpCount() {
        return evaluationScheme == null || evaluationScheme.getTpCountExpected() == null
                ? 0 : evaluationScheme.getTpCountExpected();
    }

    public double minApprovalGrade() {
        return evaluationScheme == null || evaluationScheme.getMinApprovalGrade() == null
                ? 4.0 : evaluationScheme.getMinApprovalGrade().value().doubleValue();
    }
}
