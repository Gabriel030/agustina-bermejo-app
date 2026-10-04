package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProgramCohortResponse(Long id, String intake, LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart, String createdByName, LocalDateTime createdAt,
                                    Integer enrollmentFeeMonth) {

    public static ProgramCohortResponse from(ProgramCohort cohort) {
        return new ProgramCohortResponse(
                cohort.getId(), cohort.getIntake().name(), cohort.getAdmissionStart(), cohort.getAdmissionEnd(), cohort.getCursadaStart(),
                cohort.getCreatedBy().getName() + " " + cohort.getCreatedBy().getLastname(), cohort.getCreatedAt(), cohort.getEnrollmentFeeMonth());
    }
}
