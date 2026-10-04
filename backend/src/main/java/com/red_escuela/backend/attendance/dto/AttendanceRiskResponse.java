package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.shared.domain.Percentage;

public record AttendanceRiskResponse(
        boolean calculable, boolean libre, Percentage absencePercent, Percentage maxAbsencePercent) {
}
