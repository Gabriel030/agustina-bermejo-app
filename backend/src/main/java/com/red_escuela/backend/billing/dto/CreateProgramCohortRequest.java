package com.red_escuela.backend.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.CohortIntake;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProgramCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart,
        @JsonProperty("entryFeeDiscountPoints") Percentage entryFeeDiscountPercent) {

    public CreateProgramCohortRequest(CohortIntake intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart) {
        this(intake, admissionStart, admissionEnd, cursadaStart, null);
    }
}
