package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.api.ReminderPaymentGoalQuery;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricGoal;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class ConfiguredReminderPaymentGoalQuery implements ReminderPaymentGoalQuery {

    private final DashboardGoalsProvider goals;

    public ConfiguredReminderPaymentGoalQuery(DashboardGoalsProvider goals) {
        this.goals = goals;
    }

    @Override
    public Optional<Percentage> findPercentageOfRemindersFollowedByPaymentAtLeast() {
        return goals.findGoalInEffect(GoalIndicator.REMINDER_PAYMENT_WITHIN_72H)
                .map(MetricGoal::threshold)
                .filter(PercentValue.class::isInstance)
                .map(threshold -> ((PercentValue) threshold).percentage());
    }
}
