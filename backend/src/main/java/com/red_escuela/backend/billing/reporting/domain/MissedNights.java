package com.red_escuela.backend.billing.reporting.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

public final class MissedNights {

    private MissedNights() {
    }

    public static long countIn(YearMonth month, LocalDate firstRecordedNight, LocalDate today, Set<LocalDate> recordedNights) {
        LocalDate first = month.atDay(1).isBefore(firstRecordedNight) ? firstRecordedNight : month.atDay(1);
        LocalDate lastDayThatShouldHaveRun = today.minusDays(1);
        LocalDate last = month.atEndOfMonth().isBefore(lastDayThatShouldHaveRun) ? month.atEndOfMonth() : lastDayThatShouldHaveRun;
        long missed = 0;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (!recordedNights.contains(day)) {
                missed++;
            }
        }
        return missed;
    }
}
