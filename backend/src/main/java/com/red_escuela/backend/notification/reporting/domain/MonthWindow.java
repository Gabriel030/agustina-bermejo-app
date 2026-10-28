package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.notification.reporting.exception.InvalidMonthRangeException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MonthWindow(YearMonth from, YearMonth to) {

    public static final int MAXIMUM_MONTHS = 36;

    public MonthWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to) || ChronoUnit.MONTHS.between(from, to) + 1 > MAXIMUM_MONTHS) {
            throw new InvalidMonthRangeException(from, to);
        }
    }

    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month);
        }
        return List.copyOf(months);
    }

    public boolean contains(YearMonth month) {
        return !month.isBefore(from) && !month.isAfter(to);
    }

    public int indexOf(YearMonth month) {
        return (int) ChronoUnit.MONTHS.between(from, month);
    }

    public LocalDate firstDay() {
        return from.atDay(1);
    }

    public LocalDate dayAfterLastDay() {
        return to.plusMonths(1).atDay(1);
    }
}
