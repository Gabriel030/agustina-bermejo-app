package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.shared.domain.Grade;
import java.time.LocalDate;
import java.util.List;

public record TeacherGradeGridResponse(
        Long sectionId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String evaluationSchemeName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade minRegularGrade,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade minApprovalGrade,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade promotionMinGrade,
        List<GradeGridColumnResponse> columns,
        List<GradeGridStudentResponse> students,
        LocalDate correctionDeadline) {
}
