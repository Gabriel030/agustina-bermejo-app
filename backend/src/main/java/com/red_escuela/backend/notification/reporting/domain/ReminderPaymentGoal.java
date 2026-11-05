package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ReminderPaymentGoal(Percentage atLeast) {

    public ReminderPaymentGoal {
        Objects.requireNonNull(atLeast, "atLeast");
    }

    public ReminderGoalEvaluation evaluate(BigDecimal percentPoints) {
        if (percentPoints == null) {
            return ReminderGoalEvaluation.NO_DATA;
        }
        return percentPoints.compareTo(atLeast.points()) >= 0 ? ReminderGoalEvaluation.MET : ReminderGoalEvaluation.BELOW;
    }

    public List<ReminderGoalEvaluation> evaluateEach(List<BigDecimal> percentPointsByMonth) {
        return percentPointsByMonth.stream().map(this::evaluate).toList();
    }
}
