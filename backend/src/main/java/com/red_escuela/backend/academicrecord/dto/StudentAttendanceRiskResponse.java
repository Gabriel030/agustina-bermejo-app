package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AttendanceRiskResponse")
public record StudentAttendanceRiskResponse(
        boolean calculable, boolean libre, Percentage absencePercent, Percentage maxAbsencePercent) {

    public static StudentAttendanceRiskResponse from(AttendanceStanding standing) {
        return new StudentAttendanceRiskResponse(
                standing.calculable(), standing.exceedsAbsenceLimit(),
                standing.calculable() ? standing.absencePercent() : null, standing.maxAbsencePercent());
    }
}
