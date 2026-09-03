package com.red_escuela.backend.dto;

public record RosterEntryResponse(
        Long enrollmentId, Long studentId, String studentName, String dni,
        long absenceCount, Double attendancePercentage, boolean atRisk) {
}
