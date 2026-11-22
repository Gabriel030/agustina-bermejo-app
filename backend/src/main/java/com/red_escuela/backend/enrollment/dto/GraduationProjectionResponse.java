package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.policy.GraduationProjection;
import com.red_escuela.backend.enrollment.policy.CreditsPerPeriodSource;
import com.red_escuela.backend.enrollment.policy.ProjectionBlockReason;
import com.red_escuela.backend.enrollment.policy.ProjectionConfidence;
import com.red_escuela.backend.enrollment.policy.ProjectionMethod;
import com.red_escuela.backend.enrollment.policy.ProjectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record GraduationProjectionResponse(
        Long programEnrollmentId, ProjectionStatus status, @Schema(nullable = true) Integer estimatedPeriods,
        @Schema(nullable = true) PeriodRangeResponse range, @Schema(nullable = true) Integer estimatedGraduationYear,
        String periodUnit, @Schema(nullable = true) ProjectionMethod method,
        @Schema(nullable = true) ProjectionConfidence confidence, @Schema(nullable = true) ProjectionBasisResponse basis,
        List<ProjectionMessageResponse> assumptions, List<ProjectionMessageResponse> warnings,
        List<ProjectionBlockReason> reasons) {

    public static final String PERIOD_UNIT_SEMESTER = "SEMESTER";

    public record PeriodRangeResponse(int minPeriods, int maxPeriods) {
    }

    public record ProjectionBasisResponse(
            int remainingCredits, int remainingMandatorySubjects, int remainingElectiveSubjects,
            BigDecimal creditsPerPeriod, CreditsPerPeriodSource creditsPerPeriodSource, int closedPeriods,
            int criticalPathPeriods) {
    }

    public record ProjectionMessageResponse(String code, String message) {
    }

    public static GraduationProjectionResponse from(GraduationProjection view) {
        GraduationProjection.Basis basis = view.basis();
        return new GraduationProjectionResponse(view.programEnrollmentId(), view.status(), view.estimatedPeriods(),
                view.range() == null ? null
                        : new PeriodRangeResponse(view.range().minPeriods(), view.range().maxPeriods()),
                view.estimatedGraduationYear(), PERIOD_UNIT_SEMESTER, view.method(), view.confidence(),
                basis == null ? null
                        : new ProjectionBasisResponse(basis.remainingCredits(), basis.remainingMandatorySubjects(),
                                basis.remainingElectiveSubjects(), basis.creditsPerPeriod(),
                                basis.creditsPerPeriodSource(), basis.closedPeriods(), basis.criticalPathPeriods()),
                view.assumptions().stream().map(message -> new ProjectionMessageResponse(message.code(), message.message())).toList(),
                view.warnings().stream().map(message -> new ProjectionMessageResponse(message.code(), message.message())).toList(),
                view.reasons());
    }
}
