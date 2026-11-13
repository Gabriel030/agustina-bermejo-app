package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

public record RosterEntryResponse(
        Long enrollmentId, Long studentId, String studentName, String dni,
        long absenceCount, Percentage attendancePercent, boolean atRisk,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) long classesHeld,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage maxAbsenceRate,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer remainingAbsences,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage absencePercent,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) long pendingJustificationAbsences) {
}
