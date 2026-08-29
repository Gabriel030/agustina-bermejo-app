package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateProgramCohortRequest(
        @NotNull Long programId,
        String label,
        @NotNull Integer ordenEnAnio,
        LocalDateTime admissionStart,
        LocalDateTime admissionEnd,
        @NotNull LocalDate cursadaStart) {
}
