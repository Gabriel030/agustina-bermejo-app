package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AttendancePercentFormat {

    private static final int DECIMALS = 1;

    private AttendancePercentFormat() {
    }

    public static String plain(Percentage percentage) {
        return percentage == null ? "-" : label(points(percentage, RoundingMode.HALF_UP));
    }

    public static String absence(Percentage absencePercent, Percentage maxAbsencePercent, boolean exceedsAbsenceLimit) {
        if (absencePercent == null) {
            return "-";
        }
        BigDecimal shown = points(absencePercent, RoundingMode.HALF_UP);
        if (maxAbsencePercent != null) {
            BigDecimal max = maxAbsencePercent.points();
            if (exceedsAbsenceLimit && shown.compareTo(max) <= 0) {
                shown = points(absencePercent, RoundingMode.CEILING);
            } else if (!exceedsAbsenceLimit && shown.compareTo(max) > 0) {
                shown = points(absencePercent, RoundingMode.FLOOR);
            }
        }
        return label(shown);
    }

    private static BigDecimal points(Percentage percentage, RoundingMode mode) {
        return percentage.fraction().movePointRight(2).setScale(DECIMALS, mode);
    }

    private static String label(BigDecimal points) {
        return points.toPlainString() + "%";
    }
}
