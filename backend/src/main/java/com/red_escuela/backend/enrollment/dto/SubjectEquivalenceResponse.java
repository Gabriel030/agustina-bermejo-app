package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubjectEquivalenceResponse(
        Long id,
        Long subjectId,
        @Schema(nullable = true) String subjectCode,
        @Schema(nullable = true) String subjectName,
        @Schema(nullable = true) Integer credits,
        String originInstitution,
        String originSubjectName,
        @Schema(nullable = true) Grade originScore,
        LocalDate recognizedOn,
        String resolutionNumber,
        @Schema(nullable = true) EquivalenceDocumentResponse document,
        String reason,
        String status,
        EquivalenceActorResponse recordedBy,
        LocalDateTime recordedAt,
        @Schema(nullable = true) EquivalenceActorResponse voidedBy,
        @Schema(nullable = true) LocalDateTime voidedAt,
        @Schema(nullable = true) String voidReason,
        boolean inCurrentPlan) {
}
