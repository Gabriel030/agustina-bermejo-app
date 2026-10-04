package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.shared.domain.Percentage;

public record RosterEntryResponse(
        Long enrollmentId, Long studentId, String studentName, String dni,
        long absenceCount, Percentage attendancePercent, boolean atRisk) {
}
