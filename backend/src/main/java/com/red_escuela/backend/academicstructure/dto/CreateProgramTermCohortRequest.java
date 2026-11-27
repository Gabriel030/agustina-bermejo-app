package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProgramTermCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart) {
}
