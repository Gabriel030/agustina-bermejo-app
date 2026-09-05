package com.red_escuela.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreatePromotionRequest(
        @NotBlank String description,
        @NotNull LocalDate eligibilityValidFrom,
        @NotNull LocalDate eligibilityValidTo,
        @Valid @NotEmpty List<CreatePromotionBenefitRequest> benefits) {
}
