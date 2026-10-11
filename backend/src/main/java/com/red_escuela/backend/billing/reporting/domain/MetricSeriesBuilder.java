package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MetricSeriesBuilder {

    private final MonthRange range;
    private final Currency currency;
    private final Map<String, MetricUnit> unitsByKey = new LinkedHashMap<>();
    private final Map<String, MetricValue[]> valuesByKey = new LinkedHashMap<>();

    public MetricSeriesBuilder(MonthRange range, Currency currency) {
        this.range = Objects.requireNonNull(range, "range");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public MetricSeriesBuilder declare(String key, MetricUnit unit) {
        MetricUnit previous = unitsByKey.putIfAbsent(key, unit);
        if (previous != null && previous != unit) {
            throw new IllegalArgumentException("Series " + key + " was already declared as " + previous);
        }
        valuesByKey.computeIfAbsent(key, ignored -> new MetricValue[range.months().size()]);
        return this;
    }

    public MetricSeriesBuilder put(String key, YearMonth month, MetricValue value) {
        if (!range.contains(month)) {
            return this;
        }
        declare(key, value.unit());
        valuesByKey.get(key)[range.indexOf(month)] = value;
        return this;
    }

    public List<MetricSeries> build() {
        List<MetricSeries> series = new ArrayList<>();
        unitsByKey.forEach((key, unit) -> {
            MetricValue[] recorded = valuesByKey.get(key);
            List<MetricValue> values = new ArrayList<>();
            for (MetricValue value : recorded) {
                values.add(value != null ? value : unit.valueWhenMonthHasNoActivity(currency));
            }
            series.add(new MetricSeries(key, unit, values));
        });
        return List.copyOf(series);
    }
}
