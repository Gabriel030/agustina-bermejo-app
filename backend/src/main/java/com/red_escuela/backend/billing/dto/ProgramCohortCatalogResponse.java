package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public record ProgramCohortCatalogResponse(Long id, Long programId, String label, Integer ordenEnAnio, LocalDate cursadaStart,
                                           LocalDate admissionStart, LocalDate admissionEnd, boolean admissionOpen,
                                           String intake, Long planId, String planName, @Schema(nullable = true) LocalDateTime openedAt,
                                           List<ResponsiblePreceptor> responsiblePreceptors) {

    public record ResponsiblePreceptor(Long id, String name) {
    }

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "AR"));

    public static ProgramCohortCatalogResponse from(ProgramCohort cohort, Long programId, LocalDate today, List<ResponsiblePreceptor> responsiblePreceptors) {
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
                cohort.getCursadaStart(),
                cohort.getAdmissionStart(),
                cohort.getAdmissionEnd(),
                cohort.isAdmissionOpenOn(today),
                cohort.getIntake().name(),
                cohort.getPlan().getId(),
                cohort.getPlan().getName(),
                cohort.getOpenedAt(),
                responsiblePreceptors);
    }
}
