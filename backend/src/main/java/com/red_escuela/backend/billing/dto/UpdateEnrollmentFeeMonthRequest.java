package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateEnrollmentFeeMonthRequest(@NotNull @Min(1) @Max(12) Integer enrollmentFeeMonth) {
}
