package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProgramTermCohortResponse(Long id, Long programId, Long termId, String intake, LocalDate admissionStart, LocalDate admissionEnd,
                                        LocalDate cursadaStart, LocalDateTime createdAt, Long planId,
                                        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime openedAt) {

    public static ProgramTermCohortResponse from(ProgramCohort cohort) {
        return new ProgramTermCohortResponse(cohort.getId(), cohort.getProgramId(), cohort.getTermId(), cohort.getIntake().name(),
                cohort.getAdmissionStart(), cohort.getAdmissionEnd(), cohort.getCursadaStart(), cohort.getCreatedAt(), cohort.getPlan().getId(), cohort.getOpenedAt());
    }
}
