package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClosureReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
