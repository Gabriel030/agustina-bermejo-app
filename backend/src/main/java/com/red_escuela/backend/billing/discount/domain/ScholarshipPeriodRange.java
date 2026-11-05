package com.red_escuela.backend.billing.discount.domain;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record ScholarshipPeriodRange(YearMonth from, YearMonth to) {

    public ScholarshipPeriodRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end of the range cannot be before its start");
        }
    }

    public boolean contains(YearMonth period) {
        return !period.isBefore(from) && !period.isAfter(to);
    }

    public int monthCount() {
        return (int) ChronoUnit.MONTHS.between(from, to) + 1;
    }

    public int ordinalOf(YearMonth period) {
        if (!contains(period)) {
            throw new IllegalArgumentException("The period " + period + " is outside the range");
        }
        return (int) ChronoUnit.MONTHS.between(from, period) + 1;
    }
}
