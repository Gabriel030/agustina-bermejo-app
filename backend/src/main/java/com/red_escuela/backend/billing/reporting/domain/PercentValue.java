package com.red_escuela.backend.billing.reporting.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.util.Objects;

public record PercentValue(@JsonValue Percentage percentage) implements MetricValue {

    public PercentValue {
        Objects.requireNonNull(percentage, "percentage");
    }

    @Override
    public MetricUnit unit() {
        return MetricUnit.PERCENT;
    }

    @Override
    public BigDecimal magnitude() {
        return percentage.fraction();
    }
}
