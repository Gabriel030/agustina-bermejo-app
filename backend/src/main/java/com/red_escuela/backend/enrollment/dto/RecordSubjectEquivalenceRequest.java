package com.red_escuela.backend.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordSubjectEquivalenceRequest(
        @NotNull Long subjectId,
        @NotBlank @Size(max = 255) String originInstitution,
        @NotBlank @Size(max = 255) String originSubjectName,
        @JsonProperty("originScore") @Schema(name = "originScore", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) BigDecimal originMark,
        @NotNull LocalDate recognizedOn,
        @NotBlank @Size(max = 100) String resolutionNumber,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long studentDocumentId,
        @NotNull String reason) {
}
