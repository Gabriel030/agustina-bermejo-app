package com.red_escuela.backend.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record YearBillingConfigurationRequest(
        @Min(1) @Max(2) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer enrollmentFeeCount,
        @Min(1) @Max(12) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer firstEnrollmentFeeMonth,
        @Min(1) @Max(12) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer secondEnrollmentFeeMonth,
        @Min(0) @Max(6) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer enrollmentFeeLeadMonths,
        @Min(1) @Max(60) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer lateEnrollmentFeeDueDays,
        @Min(1) @Max(60) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer recurringFeeDueDays,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean withdrawSuspendedAtTermEnd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean paysWhilePaused,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean blocksEnrollment,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean blocksExamRegistration,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean blocksCertificates,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean requireEnrollmentFeeForFinalExamOnlyStudents) {

    public static YearBillingConfigurationRequest defaults() {
        return new YearBillingConfigurationRequest(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
