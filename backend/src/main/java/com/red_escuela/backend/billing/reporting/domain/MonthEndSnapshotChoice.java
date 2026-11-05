package com.red_escuela.backend.billing.reporting.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public record MonthEndSnapshotChoice(LocalDate snapshotDate, boolean partial) {

    public MonthEndSnapshotChoice {
        Objects.requireNonNull(snapshotDate, "snapshotDate");
    }

    public static Optional<MonthEndSnapshotChoice> choose(YearMonth month, Collection<LocalDate> snapshotDates) {
        return snapshotDates.stream()
                .filter(date -> YearMonth.from(date).equals(month))
                .max(LocalDate::compareTo)
                .map(date -> new MonthEndSnapshotChoice(date, !date.equals(month.atEndOfMonth())));
    }
}
