package com.red_escuela.backend.notification.reporting.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ReminderMetricSeries(String key, ReminderMetricUnit unit, List<BigDecimal> values) {

    public ReminderMetricSeries {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(unit, "unit");
        values = Collections.unmodifiableList(new ArrayList<>(values));
    }
}
