package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ClosureSheetResponse(
        Long closureId,
        Long sectionId,
        String sectionName,
        String subjectName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String programName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String termLabel,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDate termEndDate,
        List<SectionInstructorSummary> instructors,
        ClosureStatus status,
        ClosureSheetAvailabilityResponse availability,
        ClosureSheetSchemeResponse scheme,
        List<ClosureSheetColumnResponse> columns,
        List<ClosureSheetStudentResponse> students,
        ClosureSheetSummaryResponse summary,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String reportNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime closedAt) {
}
