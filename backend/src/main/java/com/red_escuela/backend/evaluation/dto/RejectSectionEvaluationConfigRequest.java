package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectSectionEvaluationConfigRequest(@NotBlank String reason) {
}
