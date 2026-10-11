package com.red_escuela.backend.billing.reporting.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

public final class BusinessDays {

    private BusinessDays() {
    }

    public static int elapsedBetween(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end cannot be before the start");
        }
        int days = 0;
        for (LocalDate day = start.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }
}
