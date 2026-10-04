package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.ReinstatementDecision;
import jakarta.validation.constraints.NotNull;

public record ReinstateEnrollmentRequest(@NotNull ReinstatementDecision decision) {
}
