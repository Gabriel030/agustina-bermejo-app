package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;

public record AttendanceRiskView(
        boolean calculable, boolean libre, Percentage absencePercent, Percentage maxAbsencePercent) {
}
