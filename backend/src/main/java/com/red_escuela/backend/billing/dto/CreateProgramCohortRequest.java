package com.red_escuela.backend.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateProgramCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart,
        @NotEmpty List<@NotNull Long> responsiblePreceptorIds,
        @JsonProperty("entryFeeDiscountPoints") Percentage entryFeeDiscountPercent) {

    public CreateProgramCohortRequest(CohortIntake intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart,
                                      List<Long> responsiblePreceptorIds) {
        this(intake, admissionStart, admissionEnd, cursadaStart, responsiblePreceptorIds, null);
    }
}
