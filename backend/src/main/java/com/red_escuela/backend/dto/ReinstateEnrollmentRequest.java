package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ReinstatementDecision;
import jakarta.validation.constraints.NotNull;

public record ReinstateEnrollmentRequest(@NotNull ReinstatementDecision decision) {
}
