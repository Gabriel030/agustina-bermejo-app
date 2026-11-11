package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.domain.ClosureAlert;
import com.red_escuela.backend.evaluation.domain.ClosureSuggestedResult;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ClosureSheetStudentResponse(
        Long enrollmentId,
        String lastname,
        String name,
        String dni,
        EnrollmentStatus enrollmentStatus,
        List<ClosureSheetGradeResponse> grades,
        int retakesUsed,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade average,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Percentage attendancePercent,
        ClosureSuggestedResult suggestedResult,
        ClosureSheetRequirementsResponse requirements,
        ClosureSheetAdministrativeResponse administrative,
        List<ClosureAlert> alerts,
        boolean exempted,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String teacherObservation,
        boolean promotionExempted) {
}
