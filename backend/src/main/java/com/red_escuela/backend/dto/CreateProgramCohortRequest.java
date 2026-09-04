package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CohortIntake;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProgramCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart) {
}
