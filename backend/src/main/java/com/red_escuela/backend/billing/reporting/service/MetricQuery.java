package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MetricPanel;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;

public interface MetricQuery {

    MetricPanel panel();

    boolean isAvailable();

    MonthlyMetric findSeries(MetricFilter filter);
}
