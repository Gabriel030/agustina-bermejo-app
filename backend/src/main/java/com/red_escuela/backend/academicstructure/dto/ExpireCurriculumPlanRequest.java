package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExpireCurriculumPlanRequest(
        @NotNull LocalDate expiresOn,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate graduationDeadline) implements SelfValidatingRequest {
}
