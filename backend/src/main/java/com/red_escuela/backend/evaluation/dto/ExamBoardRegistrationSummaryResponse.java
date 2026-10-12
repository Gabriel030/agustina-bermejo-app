package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;

public record ExamBoardRegistrationSummaryResponse(
        Long id, String studentName, String dni, String carrera, Integer attemptNumber, String status, BigDecimal finalScore) {
}
