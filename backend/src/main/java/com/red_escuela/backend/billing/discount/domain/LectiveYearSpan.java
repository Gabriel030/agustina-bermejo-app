package com.red_escuela.backend.billing.discount.domain;

import java.time.YearMonth;
import java.util.Objects;

public record LectiveYearSpan(YearMonth start, YearMonth end) {

    public LectiveYearSpan {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    public boolean contains(YearMonth period) {
        return !period.isBefore(start) && !period.isAfter(end);
    }
}
