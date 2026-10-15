package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Predicate;

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

    public int chargedDaysAfter(LocalDate day, Predicate<LocalDate> chargedOn) {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(chargedOn, "chargedOn");
        LocalDate from = firstDay.isAfter(day) ? firstDay : day.plusDays(1);
        int charged = 0;
        for (LocalDate current = from; !current.isAfter(lastDay); current = current.plusDays(1)) {
            if (chargedOn.test(current)) {
                charged++;
            }
        }
        return charged;
    }

    public YearMonth accrualMonth() {
        return YearMonth.from(firstDay);
    }
}
