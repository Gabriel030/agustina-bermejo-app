package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DailyLateFeeSchedule(LocalDate originDueDate, int gracePeriodDays) {

    public DailyLateFeeSchedule {
        Objects.requireNonNull(originDueDate, "originDueDate");
        if (gracePeriodDays < 0) {
            throw new IllegalArgumentException("gracePeriodDays must not be negative");
        }
    }

    public LocalDate firstChargeableDay() {
        return originDueDate.plusDays(1);
    }

    public boolean isPastGraceOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return date.isAfter(originDueDate.plusDays(gracePeriodDays));
    }

    public List<LateFeeAccrualPeriod> splitByCalendarMonth(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        List<LateFeeAccrualPeriod> periods = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate monthEnd = YearMonth.from(cursor).atEndOfMonth();
            LocalDate periodEnd = monthEnd.isAfter(to) ? to : monthEnd;
            periods.add(new LateFeeAccrualPeriod(cursor, periodEnd));
            cursor = periodEnd.plusDays(1);
        }
        return periods;
    }
}
