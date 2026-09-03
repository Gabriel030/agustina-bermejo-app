package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// admissionStart/admissionEnd migraron a ProgramCohort (Épica 8 Fase 2) — la
// ventana de admisión ya no es un dato de la carrera en sí, es por cohorte.
public record CreateProgramRequest(
        @NotBlank String name, @NotNull @Positive Integer totalYears,
        BigDecimal price, @NotNull @Min(0) Integer totalCreditsRequired,
        Integer installmentsPerAcademicYear) {
}
