package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSectionRequest(
        @NotBlank String name, @NotNull Long termId, String schedule,
        @NotNull @Positive Integer capacity, Long evaluationSchemeId, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Percentage maxAbsencePercent) {

    public CreateSectionRequest(String name, Long termId, String schedule, Integer capacity, Long evaluationSchemeId) {
        this(name, termId, schedule, capacity, evaluationSchemeId, null);
    }
}
