package com.red_escuela.backend.billing.reporting.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MonthlyMetric(MetricPanel panel, boolean available, boolean partial, List<YearMonth> months, List<MetricSeries> series,
                            MetricGoal goal, String goalSeriesKey) {

    public MonthlyMetric {
        Objects.requireNonNull(panel, "panel");
        months = List.copyOf(months);
        series = List.copyOf(series);
    }

    public static MonthlyMetric unavailable(MetricPanel panel, MonthRange range) {
        return new MonthlyMetric(panel, false, false, range.months(), List.of(), null, null);
    }

    public static MonthlyMetric available(MetricPanel panel, MonthRange range, List<MetricSeries> series) {
        return new MonthlyMetric(panel, true, false, range.months(), series, null, null);
    }

    public MonthlyMetric withPartial(boolean partial) {
        return new MonthlyMetric(panel, available, partial, months, series, goal, goalSeriesKey);
    }

    public MonthlyMetric withGoal(Optional<MetricGoal> goal, String goalSeriesKey) {
        return goal.map(configured -> new MonthlyMetric(panel, available, partial, months, series, configured, goalSeriesKey)).orElse(this);
    }

    public Optional<List<GoalEvaluation>> goalEvaluation() {
        if (goal == null) {
            return Optional.empty();
        }
        return series.stream()
                .filter(candidate -> candidate.key().equals(goalSeriesKey))
                .findFirst()
                .map(evaluated -> goal.evaluateEach(evaluated.values()));
    }
}
