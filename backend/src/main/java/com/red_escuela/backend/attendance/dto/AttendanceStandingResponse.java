package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.shared.domain.Percentage;
import io.swagger.v3.oas.annotations.media.Schema;

public record AttendanceStandingResponse(
        long classesHeld,
        long countableAbsences,
        long pendingJustificationAbsences,
        @Schema(nullable = true) Percentage absencePercent,
        @Schema(nullable = true) Percentage maxAbsencePercent,
        @Schema(nullable = true) Integer allowedAbsences,
        @Schema(nullable = true) Integer remainingAbsences,
        boolean exceedsAbsenceLimit) {

    public static AttendanceStandingResponse from(AttendanceStanding standing) {
        return new AttendanceStandingResponse(
                standing.classesHeld(), standing.countableAbsences(), standing.pendingJustificationAbsences(),
                standing.absencePercent(), standing.maxAbsencePercent(), standing.allowedAbsences(),
                standing.remainingAbsences(), standing.exceedsAbsenceLimit());
    }
}
