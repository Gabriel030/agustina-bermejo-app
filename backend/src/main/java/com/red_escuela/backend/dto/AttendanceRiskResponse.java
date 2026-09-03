package com.red_escuela.backend.dto;

public record AttendanceRiskResponse(
        boolean calculable, boolean libre, Double porcentajeAusentismo, Double maxAbsencePercentage) {
}
