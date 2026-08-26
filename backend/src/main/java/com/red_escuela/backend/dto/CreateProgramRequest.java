package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProgramRequest(
        @NotBlank String name, @NotNull @Positive Integer totalYears,
        BigDecimal price, @NotNull @Min(0) Integer totalCreditsRequired,
        LocalDateTime admissionStart, LocalDateTime admissionEnd) {
}
