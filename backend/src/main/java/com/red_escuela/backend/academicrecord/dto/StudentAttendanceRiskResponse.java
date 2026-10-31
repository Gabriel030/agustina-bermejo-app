package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.attendance.api.AttendanceRiskView;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AttendanceRiskResponse")
public record StudentAttendanceRiskResponse(
        boolean calculable, boolean libre, Percentage absencePercent, Percentage maxAbsencePercent) {

    public static StudentAttendanceRiskResponse from(AttendanceRiskView view) {
        return new StudentAttendanceRiskResponse(
                view.calculable(), view.libre(), view.absencePercent(), view.maxAbsencePercent());
    }
}
