package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record YearBillingConfigurationRequest(
        @Min(1) @Max(2) Integer enrollmentFeeCount,
        @Min(1) @Max(12) Integer firstEnrollmentFeeMonth,
        @Min(1) @Max(12) Integer secondEnrollmentFeeMonth,
        @Min(0) @Max(120) Integer enrollmentFeeLeadDays,
        Boolean blocksEnrollment,
        Boolean blocksExamRegistration,
        Boolean blocksCertificates,
        Boolean requireEnrollmentFeeForFinalExamOnlyStudents) {

    public static YearBillingConfigurationRequest defaults() {
        return new YearBillingConfigurationRequest(null, null, null, null, null, null, null, null);
    }
}
