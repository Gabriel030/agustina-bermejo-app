package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Percentage;

public record SubjectAttendanceSummaryResponse(String subjectName, Percentage attendancePercent) {
}
