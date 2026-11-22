package com.red_escuela.backend.enrollment.policy;


import java.math.BigDecimal;
import java.util.List;

public record GraduationProjection(
        Long programEnrollmentId, ProjectionStatus status, Integer estimatedPeriods, PeriodRange range,
        Integer estimatedGraduationYear, ProjectionMethod method, ProjectionConfidence confidence, Basis basis,
        List<Message> assumptions, List<Message> warnings, List<ProjectionBlockReason> reasons) {

    public record PeriodRange(int minPeriods, int maxPeriods) {
    }

    public record Basis(
            int remainingCredits, int remainingMandatorySubjects, int remainingElectiveSubjects,
            BigDecimal creditsPerPeriod, CreditsPerPeriodSource creditsPerPeriodSource, int closedPeriods,
            int criticalPathPeriods) {
    }

    public record Message(String code, String message) {
    }
}
