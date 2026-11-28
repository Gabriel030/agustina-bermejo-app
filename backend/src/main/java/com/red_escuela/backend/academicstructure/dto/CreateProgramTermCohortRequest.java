package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateProgramTermCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<Long> responsiblePreceptorIds) {
}
