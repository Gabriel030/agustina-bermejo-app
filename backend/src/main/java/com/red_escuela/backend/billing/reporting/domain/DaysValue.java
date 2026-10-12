package com.red_escuela.backend.billing.reporting.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DaysValue(@JsonValue BigDecimal days) implements MetricValue {

    public DaysValue {
        Objects.requireNonNull(days, "days");
        if (days.signum() < 0) {
            throw new IllegalArgumentException("A number of days cannot be negative");
        }
        days = days.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public MetricUnit unit() {
        return MetricUnit.DAYS;
    }

    @Override
    public BigDecimal magnitude() {
        return days;
    }
}
