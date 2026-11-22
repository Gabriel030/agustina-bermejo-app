package com.red_escuela.backend.graduation.documentsettings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateDocumentIssuanceSettingsRequest(
        @Min(0) @Max(365) int examRegistrationAdvanceDays,
        @Min(1) @Max(365) int examAttendanceValidityDays,
        @Min(1) @Max(365) int approvedSubjectsValidityDays,
        @Min(1) @Max(50) int dailyIssuanceLimitPerType,
        long version) {
}
