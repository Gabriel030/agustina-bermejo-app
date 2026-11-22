package com.red_escuela.backend.graduation.documentsettings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentIssuanceSettingsRequest(
        @NotNull @Min(0) @Max(365) Integer examRegistrationAdvanceDays,
        @NotNull @Min(1) @Max(365) Integer examAttendanceValidityDays,
        @NotNull @Min(1) @Max(365) Integer approvedSubjectsValidityDays,
        @NotNull @Min(1) @Max(50) Integer dailyIssuanceLimitPerType,
        @NotNull Long version) {
}
