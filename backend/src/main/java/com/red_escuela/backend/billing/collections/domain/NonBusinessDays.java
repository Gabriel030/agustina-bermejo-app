package com.red_escuela.backend.billing.collections.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public record NonBusinessDays(Set<LocalDate> holidays) {

    public NonBusinessDays {
        holidays = Set.copyOf(holidays);
    }

    public static NonBusinessDays weekendsOnly() {
        return new NonBusinessDays(Set.of());
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    public LocalDate nextBusinessDayAfter(LocalDate date) {
        LocalDate candidate = date.plusDays(1);
        while (!isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public LocalDate previousBusinessDayBefore(LocalDate date) {
        LocalDate candidate = date.minusDays(1);
        while (!isBusinessDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }
}
