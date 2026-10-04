package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.CohortIntake;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateProgramCohortRequest(
        @NotNull CohortIntake intake, @NotNull LocalDate admissionStart, @NotNull LocalDate admissionEnd, @NotNull LocalDate cursadaStart,
        @Min(1) @Max(12) Integer enrollmentFeeMonth,
        Percentage entryFeeDiscountPoints) {

    public CreateProgramCohortRequest(CohortIntake intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart, Integer enrollmentFeeMonth) {
        this(intake, admissionStart, admissionEnd, cursadaStart, enrollmentFeeMonth, null);
    }

    public CreateProgramCohortRequest(CohortIntake intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart) {
        this(intake, admissionStart, admissionEnd, cursadaStart, null, null);
    }
}
