package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;

public record BillingDashboardGoals(Percentage onTimePaymentAtLeast, Percentage overdueRatioAtMost, Integer receiptReviewBusinessDaysAtMost,
                                    Percentage reminderPaidWithin72HoursAtLeast) implements ConfigurationValues {

    private static final Percentage DEFAULT_ON_TIME_PAYMENT = Percentage.ofPoints(new BigDecimal("85"));

    public BillingDashboardGoals {
        if (receiptReviewBusinessDaysAtMost != null && receiptReviewBusinessDaysAtMost < 1) {
            throw new InvalidConfigurationValuesException("El plazo de revisión de comprobantes debe ser de al menos 1 día hábil");
        }
    }

    public static BillingDashboardGoals initial() {
        return new BillingDashboardGoals(DEFAULT_ON_TIME_PAYMENT, null, null, null);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.BILLING_DASHBOARD_GOALS;
    }
}
