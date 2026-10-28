package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReminderMetricSeriesBuilder {

    private final MonthWindow window;
    private final Map<String, ReminderMetricUnit> unitsByKey = new LinkedHashMap<>();
    private final Map<String, BigDecimal[]> valuesByKey = new LinkedHashMap<>();

    public ReminderMetricSeriesBuilder(MonthWindow window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    public ReminderMetricSeriesBuilder declareCount(String key) {
        if (!unitsByKey.containsKey(key)) {
            BigDecimal[] zeros = new BigDecimal[window.months().size()];
            Arrays.fill(zeros, BigDecimal.ZERO);
            unitsByKey.put(key, ReminderMetricUnit.COUNT);
            valuesByKey.put(key, zeros);
        }
        return this;
    }

    public ReminderMetricSeriesBuilder declarePercent(String key) {
        if (!unitsByKey.containsKey(key)) {
            unitsByKey.put(key, ReminderMetricUnit.PERCENT);
            valuesByKey.put(key, new BigDecimal[window.months().size()]);
        }
        return this;
    }

    public ReminderMetricSeriesBuilder addCount(String key, YearMonth month, long amount) {
        if (!window.contains(month)) {
            return this;
        }
        declareCount(key);
        int index = window.indexOf(month);
        BigDecimal[] values = valuesByKey.get(key);
        values[index] = values[index].add(BigDecimal.valueOf(amount));
        return this;
    }

    public ReminderMetricSeriesBuilder putPercent(String key, YearMonth month, Percentage percentage) {
        if (!window.contains(month)) {
            return this;
        }
        declarePercent(key);
        valuesByKey.get(key)[window.indexOf(month)] = percentage.points();
        return this;
    }

    public List<ReminderMetricSeries> build() {
        List<ReminderMetricSeries> series = new ArrayList<>();
        unitsByKey.forEach((key, unit) -> series.add(new ReminderMetricSeries(key, unit, Arrays.asList(valuesByKey.get(key)))));
        return List.copyOf(series);
    }
}
