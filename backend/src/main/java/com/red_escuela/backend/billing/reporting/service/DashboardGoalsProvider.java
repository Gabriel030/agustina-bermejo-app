package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.reporting.domain.DaysValue;
import com.red_escuela.backend.billing.reporting.domain.GoalIndicator;
import com.red_escuela.backend.billing.reporting.domain.MetricGoal;
import com.red_escuela.backend.billing.reporting.domain.PercentValue;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class DashboardGoalsProvider {

    private final BillingConfigurationReader configurationReader;

    public DashboardGoalsProvider(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    public Optional<MetricGoal> findGoalInEffect(GoalIndicator indicator) {
        BillingDashboardGoals goals = configurationReader.billingDashboardGoalsEffectiveOn(ApplicationTime.today());
        return switch (indicator) {
            case ON_TIME_PAYMENT -> percentGoal(indicator, goals.onTimePaymentAtLeast());
            case OVERDUE_RATIO -> percentGoal(indicator, goals.overdueRatioAtMost());
            case REMINDER_PAYMENT_WITHIN_72H -> percentGoal(indicator, goals.reminderPaidWithin72HoursAtLeast());
            case RECEIPT_REVIEW_TIME -> Optional.ofNullable(goals.receiptReviewBusinessDaysAtMost())
                    .map(days -> new MetricGoal(indicator, new DaysValue(BigDecimal.valueOf(days))));
        };
    }

    private static Optional<MetricGoal> percentGoal(GoalIndicator indicator, Percentage threshold) {
        return Optional.ofNullable(threshold).map(percentage -> new MetricGoal(indicator, new PercentValue(percentage)));
    }
}
