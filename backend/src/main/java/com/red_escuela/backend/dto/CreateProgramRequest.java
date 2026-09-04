package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// admissionStart/admissionEnd live in ProgramCohort (Fase 6.1) — the admission window is not a
// property of the program itself, it's per cohort.
public record CreateProgramRequest(
        @NotBlank String name, @NotNull @Positive Integer totalYears,
        BigDecimal price, BigDecimal enrollmentFeeAmount, @NotNull @Min(0) Integer totalCreditsRequired) {
}
