package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record LateFeeAccrualPeriod(LocalDate firstDay, LocalDate lastDay) {

    public LateFeeAccrualPeriod {
        Objects.requireNonNull(firstDay, "firstDay");
        Objects.requireNonNull(lastDay, "lastDay");
        if (firstDay.isAfter(lastDay)) {
            throw new IllegalArgumentException("The first day of a late fee period must not be after its last day");
        }
        if (!YearMonth.from(firstDay).equals(YearMonth.from(lastDay))) {
            throw new IllegalArgumentException("A late fee period must stay within one calendar month");
        }
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(firstDay, lastDay) + 1;
    }

    public int daysAfter(LocalDate day) {
        Objects.requireNonNull(day, "day");
        if (!lastDay.isAfter(day)) {
            return 0;
        }
        LocalDate from = firstDay.isAfter(day) ? firstDay : day.plusDays(1);
        return (int) ChronoUnit.DAYS.between(from, lastDay) + 1;
    }

    public YearMonth accrualMonth() {
        return YearMonth.from(firstDay);
    }
}
