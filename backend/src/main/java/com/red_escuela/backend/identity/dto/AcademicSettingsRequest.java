package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AcademicSettingsRequest(
        @Min(0) @Max(60) int justificationDeadlineBusinessDays,
        @Min(1) @Max(120) int evaluationConfigDueDays) {
}
