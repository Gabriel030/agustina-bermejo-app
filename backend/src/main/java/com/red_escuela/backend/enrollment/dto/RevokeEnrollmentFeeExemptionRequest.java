package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record RevokeEnrollmentFeeExemptionRequest(@Size(max = 500) @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String notes) {
}
