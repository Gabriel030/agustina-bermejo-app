package com.red_escuela.backend.billing.reporting.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;

public record CountValue(@JsonValue long count) implements MetricValue {

    public CountValue {
        if (count < 0) {
            throw new IllegalArgumentException("A count cannot be negative");
        }
    }

    @Override
    public MetricUnit unit() {
        return MetricUnit.COUNT;
    }

    @Override
    public BigDecimal magnitude() {
        return BigDecimal.valueOf(count);
    }
}
