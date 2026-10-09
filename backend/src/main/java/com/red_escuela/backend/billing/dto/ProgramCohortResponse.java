package com.red_escuela.backend.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProgramCohortResponse(Long id, String intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart, String createdByName, LocalDateTime createdAt,
                                    @JsonProperty("entryFeeDiscountPoints") Percentage entryFeeDiscountPercent) {

    public static ProgramCohortResponse from(ProgramCohort cohort, Percentage entryFeeDiscount) {
        return new ProgramCohortResponse(
                cohort.getId(), cohort.getIntake().name(), cohort.getAdmissionStart(), cohort.getAdmissionEnd(), cohort.getCursadaStart(),
                cohort.getCreatedBy().getName() + " " + cohort.getCreatedBy().getLastname(), cohort.getCreatedAt(),
                entryFeeDiscount);
    }
}
