package com.red_escuela.backend.billing.configuration.policy;

import java.time.YearMonth;

final class EnrollmentFeePeriods {
    private static final int PREVIOUS_YEAR_THRESHOLD_MONTHS = 6;

    private EnrollmentFeePeriods() {
    }

    static YearMonth firstPeriod(YearMonth termStart, int firstMonth) {
        boolean belongsToPreviousYear = firstMonth - termStart.getMonthValue() >= PREVIOUS_YEAR_THRESHOLD_MONTHS;
        return YearMonth.of(belongsToPreviousYear ? termStart.getYear() - 1 : termStart.getYear(), firstMonth);
    }

    static YearMonth secondPeriod(YearMonth termStart, int secondMonth) {
        return YearMonth.of(termStart.getYear(), secondMonth);
    }
}
