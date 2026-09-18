package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProgramRequest(
        @NotBlank String name, @NotNull @Positive Integer totalYears,
        BigDecimal price, BigDecimal enrollmentFeeAmount, @NotNull @Min(0) Integer totalCreditsRequired) {
}
