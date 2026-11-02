package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateSectionRequest(
        String name, String schedule, Integer capacity, Long evaluationSchemeId, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage maxAbsencePercent) {

    public UpdateSectionRequest(String name, String schedule, Integer capacity, Long evaluationSchemeId) {
        this(name, schedule, capacity, evaluationSchemeId, null);
    }
}
