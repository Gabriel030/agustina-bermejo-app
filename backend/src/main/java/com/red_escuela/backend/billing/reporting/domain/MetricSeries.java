package com.red_escuela.backend.billing.reporting.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record MetricSeries(String key, MetricUnit unit, List<MetricValue> values) {

    public MetricSeries {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(unit, "unit");
        values = Collections.unmodifiableList(new ArrayList<>(values));
        for (MetricValue value : values) {
            if (value != null && value.unit() != unit) {
                throw new IllegalArgumentException("Series " + key + " mixes " + value.unit() + " values into a " + unit + " series");
            }
        }
    }
}
