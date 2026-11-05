package com.red_escuela.backend.notification.reporting.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CollectionReminderMetric(List<YearMonth> months, List<ReminderMetricSeries> series, ReminderPaymentGoal goal) {

    public CollectionReminderMetric {
        months = List.copyOf(months);
        series = List.copyOf(series);
    }

    public Optional<ReminderPaymentGoal> configuredGoal() {
        return Optional.ofNullable(goal);
    }

    public Optional<List<ReminderGoalEvaluation>> goalEvaluation() {
        return configuredGoal().flatMap(configured -> series.stream()
                .filter(candidate -> Objects.equals(candidate.key(), ReminderMetricSeriesAssembler.PAID_WITHIN_72_HOURS_PERCENT))
                .findFirst()
                .map(percent -> configured.evaluateEach(percent.values())));
    }
}
