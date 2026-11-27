package com.red_escuela.backend.attendance.policy;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.identity.api.StudentAttendanceRisk;

import java.math.BigDecimal;

public final class StudentAttendanceRiskPolicy {

    private static final BigDecimal NEAR_LIMIT_SHARE = new BigDecimal("0.8");

    private StudentAttendanceRiskPolicy() {
    }

    public static StudentAttendanceRisk classify(AttendanceStanding standing) {
        if (!standing.calculable() || standing.absencePercent() == null) {
            return StudentAttendanceRisk.NONE;
        }
        if (standing.exceedsAbsenceLimit()) {
            return StudentAttendanceRisk.FREE;
        }
        BigDecimal threshold = standing.maxAbsencePercent().fraction().multiply(NEAR_LIMIT_SHARE);
        boolean nearLimit = standing.countableAbsences() > 0
                && standing.absencePercent().fraction().compareTo(threshold) >= 0;
        return nearLimit ? StudentAttendanceRisk.NEAR_LIMIT : StudentAttendanceRisk.NONE;
    }

    public static StudentAttendanceRisk worst(StudentAttendanceRisk first, StudentAttendanceRisk second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
