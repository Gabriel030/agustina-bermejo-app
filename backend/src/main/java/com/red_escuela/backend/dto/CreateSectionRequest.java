package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSectionRequest(
        @NotBlank String name, @NotNull Long termId, String schedule,
        @NotNull @Positive Integer capacity, Long evaluationSchemeId) {
}
