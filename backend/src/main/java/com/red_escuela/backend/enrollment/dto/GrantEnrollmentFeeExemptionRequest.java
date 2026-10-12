package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GrantEnrollmentFeeExemptionRequest(@NotNull Long templateId, @NotNull @Min(1) @Max(2) Integer slot,
                                                 @NotBlank @Size(min = 10, max = 1000) String reason) {
}
