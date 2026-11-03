package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.domain.GradeGridCondition;
import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.shared.domain.Grade;
import java.util.List;

public record GradeGridStudentResponse(
        Long enrollmentId,
        String lastname,
        String name,
        String dni,
        GradeGridCondition condition,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade finalScore,
        boolean finalEnabled,
        List<GradeGridCellResponse> grades) {
}
