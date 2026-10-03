package com.red_escuela.backend.evaluation.dto;

public record ExamBoardRegistrationSummaryResponse(
        Long id, String studentName, String dni, String carrera, Integer attemptNumber, String status, Double finalScore) {
}
