package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AcademicSettingsRequest(
        @Min(0) @Max(60) int justificationDeadlineBusinessDays,
        @Min(1) @Max(120) int evaluationConfigDueDays,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean announcementEmailEnabled) {
}
