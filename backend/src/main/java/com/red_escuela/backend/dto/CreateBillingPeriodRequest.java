package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBillingPeriodRequest(
        @NotNull @Min(1) @Max(31) Integer diaInicio,
        @NotNull @Min(1) @Max(31) Integer diaFin,
        @NotNull LocalDate vigenteDesde) {
}
