package com.red_escuela.backend.billing.reporting.domain;

import java.util.List;
import java.util.Objects;

public record MetricGoal(GoalIndicator indicator, MetricValue threshold) {

    public MetricGoal {
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.unit() != MetricUnit.PERCENT && threshold.unit() != MetricUnit.DAYS) {
            throw new IllegalArgumentException("A goal threshold is a percentage or a number of days");
        }
    }

    public GoalComparator comparator() {
        return indicator.comparator();
    }

    public GoalEvaluation evaluate(MetricValue actual) {
        if (actual == null) {
            return GoalEvaluation.NO_DATA;
        }
        if (actual.unit() != threshold.unit()) {
            throw new IllegalArgumentException("Cannot compare " + actual.unit() + " against a goal in " + threshold.unit());
        }
        return comparator().isMetBy(actual.magnitude(), threshold.magnitude()) ? GoalEvaluation.MET : GoalEvaluation.BELOW;
    }

    public List<GoalEvaluation> evaluateEach(List<MetricValue> values) {
        return values.stream().map(this::evaluate).toList();
    }
}
