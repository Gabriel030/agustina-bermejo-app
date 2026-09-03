package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLateFeePolicyRequest(
        @NotNull @Min(0) Integer diasDeGracia,
        @NotNull @Positive BigDecimal porcentajeRecargoPorPeriodo,
        @NotNull @Positive Integer diasPorPeriodo,
        @NotNull @Positive Integer diasParaRiesgoDeCorte,
        @NotNull LocalDate vigenteDesde) {
}
