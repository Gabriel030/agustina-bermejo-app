package com.red_escuela.backend.billing.reporting.usecase;

import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;

public interface GetMonthlyBillingMetricsUseCase {
    MonthlyMetric getMonthlyBillingMetrics(GetMonthlyBillingMetricsCommand command);
}
