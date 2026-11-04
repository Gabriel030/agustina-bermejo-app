package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateSectionRequest(
        String name, String schedule, Integer capacity, Long evaluationSchemeId, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage maxAbsencePercent) implements SelfValidatingRequest {
    public UpdateSectionRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("schedule", schedule, 255);
        RequestLimits.requireAtMost("capacity", capacity, 10000);
    }
    public UpdateSectionRequest(String name, String schedule, Integer capacity, Long evaluationSchemeId) {
        this(name, schedule, capacity, evaluationSchemeId, null);
    }
}
