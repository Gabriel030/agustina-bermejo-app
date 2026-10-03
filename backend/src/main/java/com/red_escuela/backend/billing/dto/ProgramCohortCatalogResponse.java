package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record ProgramCohortCatalogResponse(Long id, Long programId, String label, Integer ordenEnAnio, LocalDate cursadaStart) {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "AR"));

    public static ProgramCohortCatalogResponse from(ProgramCohort cohort, Long programId) {
        String label = cohort.getCursadaStart() != null
                ? cohort.getCursadaStart().format(LABEL_FMT).substring(0, 1).toUpperCase()
                  + cohort.getCursadaStart().format(LABEL_FMT).substring(1)
                : null;
        int orden = cohort.getIntake() == CohortIntake.SECOND ? 2 : 1;
        return new ProgramCohortCatalogResponse(
                cohort.getId(),
                programId,
                label,
                orden,
                cohort.getCursadaStart());
    }
}
