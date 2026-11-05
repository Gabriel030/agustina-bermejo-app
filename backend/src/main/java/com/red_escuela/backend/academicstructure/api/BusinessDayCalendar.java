package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDate;

public interface BusinessDayCalendar {
    boolean isBusinessDay(LocalDate date);

    default LocalDate nthBusinessDayOnOrBefore(LocalDate date, int position) {
        if (position < 1) {
            throw new IllegalArgumentException("position must be at least 1");
        }
        int found = 0;
        LocalDate day = date;
        while (true) {
            if (isBusinessDay(day)) {
                found++;
                if (found == position) {
                    return day;
                }
            }
            day = day.minusDays(1);
        }
    }
}
