package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;

public record AttendanceStanding(
        long classesHeld,
        long presentClasses,
        long countableAbsences,
        long pendingJustificationAbsences,
        Percentage absencePercent,
        Percentage presencePercent,
        Percentage maxAbsencePercent,
        boolean exceedsAbsenceLimit,
        Integer allowedAbsences,
        Integer remainingAbsences) {

    public boolean calculable() {
        return maxAbsencePercent != null;
    }

    public String absencePercentLabel() {
        return AttendancePercentFormat.absence(absencePercent, maxAbsencePercent, exceedsAbsenceLimit);
    }
}
