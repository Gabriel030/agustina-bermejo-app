package com.red_escuela.backend.billing.reporting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.red_escuela.backend.billing.reporting.domain.GoalComparator;
import com.red_escuela.backend.billing.reporting.domain.GoalEvaluation;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricGoal;
import com.red_escuela.backend.billing.reporting.domain.MetricSeries;
import com.red_escuela.backend.billing.reporting.domain.MetricUnit;
import com.red_escuela.backend.billing.reporting.domain.MetricValue;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyMetricResponse(String panel, boolean available, boolean partial, List<String> months, List<SeriesResponse> series,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) GoalResponse goal) {

    public static MonthlyMetricResponse from(MonthlyMetric metric) {
        return new MonthlyMetricResponse(metric.panel().path(), metric.available(), metric.partial(),
                metric.months().stream().map(Object::toString).toList(),
                metric.series().stream().map(SeriesResponse::from).toList(),
                metric.goalEvaluation().map(evaluation -> GoalResponse.from(metric.goal(), evaluation)).orElse(null));
    }

    public record SeriesResponse(String key, MetricUnit unit, List<MetricValue> values) {

        static SeriesResponse from(MetricSeries series) {
            return new SeriesResponse(series.key(), series.unit(), series.values());
        }
    }

    public record GoalResponse(GoalIndicator indicator, GoalComparator comparator, @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal thresholdPercent,
                               @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal thresholdDays, List<GoalEvaluation> evaluation) {

        static GoalResponse from(MetricGoal goal, List<GoalEvaluation> evaluation) {
            BigDecimal percent = goal.threshold() instanceof PercentValue value ? value.percentage().points() : null;
            BigDecimal days = goal.threshold().unit() == MetricUnit.DAYS ? goal.threshold().magnitude() : null;
            return new GoalResponse(goal.indicator(), goal.comparator(), percent, days, evaluation);
        }
    }
}
