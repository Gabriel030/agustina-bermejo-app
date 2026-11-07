package com.red_escuela.backend.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ExamBoardRegistrationSummaryResponse(
        Long id, String studentName, String dni, String carrera, Integer attemptNumber, String status, @Schema(nullable = true) BigDecimal finalScore) {
}
