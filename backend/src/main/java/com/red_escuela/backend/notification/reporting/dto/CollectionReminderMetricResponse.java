package com.red_escuela.backend.notification.reporting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.red_escuela.backend.notification.reporting.domain.CollectionReminderMetric;
import com.red_escuela.backend.notification.reporting.domain.ReminderGoalEvaluation;
import com.red_escuela.backend.notification.reporting.domain.ReminderMetricSeries;
import com.red_escuela.backend.notification.reporting.domain.ReminderMetricUnit;
import com.red_escuela.backend.notification.reporting.domain.ReminderPaymentGoal;

import java.math.BigDecimal;
import java.util.List;

public record CollectionReminderMetricResponse(String panel, boolean available, boolean partial, List<String> months, List<ReminderSeriesResponse> series,
                                               @JsonInclude(JsonInclude.Include.NON_NULL) ReminderGoalResponse goal) {

    static final String PANEL = "collection-reminders";
    static final String GOAL_INDICATOR = "REMINDER_PAYMENT_WITHIN_72H";
    static final String GOAL_COMPARATOR = "AT_LEAST";

    public static CollectionReminderMetricResponse from(CollectionReminderMetric metric) {
        ReminderGoalResponse goal = metric.configuredGoal()
                .flatMap(configured -> metric.goalEvaluation().map(evaluation -> ReminderGoalResponse.from(configured, evaluation)))
                .orElse(null);
        return new CollectionReminderMetricResponse(PANEL, true, false, metric.months().stream().map(Object::toString).toList(),
                metric.series().stream().map(ReminderSeriesResponse::from).toList(), goal);
    }

    public record ReminderSeriesResponse(String key, ReminderMetricUnit unit, List<BigDecimal> values) {

        static ReminderSeriesResponse from(ReminderMetricSeries series) {
            return new ReminderSeriesResponse(series.key(), series.unit(), series.values());
        }
    }

    public record ReminderGoalResponse(String indicator, String comparator, BigDecimal thresholdPercent, List<ReminderGoalEvaluation> evaluation) {

        static ReminderGoalResponse from(ReminderPaymentGoal goal, List<ReminderGoalEvaluation> evaluation) {
            return new ReminderGoalResponse(GOAL_INDICATOR, GOAL_COMPARATOR, goal.atLeast().points(), evaluation);
        }
    }
}
