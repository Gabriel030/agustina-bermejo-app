package com.red_escuela.backend.notification.reporting.usecase;

import com.red_escuela.backend.notification.reporting.domain.CollectionReminderMetric;

import java.time.YearMonth;

public interface GetCollectionReminderMetricUseCase {
    CollectionReminderMetric getCollectionReminderMetric(YearMonth from, YearMonth to);
}
