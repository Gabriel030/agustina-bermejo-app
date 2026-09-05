package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ProgramCohort;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProgramCohortResponse(
        Long id, Long programId, String label, Integer ordenEnAnio,
        LocalDateTime admissionStart, LocalDateTime admissionEnd, LocalDate cursadaStart) {

    public static ProgramCohortResponse from(ProgramCohort cohort) {
        return new ProgramCohortResponse(
                cohort.getId(), cohort.getProgram().getId(), cohort.getLabel(), cohort.getOrderInYear(),
                cohort.getAdmissionStart(), cohort.getAdmissionEnd(), cohort.getCourseStart());
    }
}
