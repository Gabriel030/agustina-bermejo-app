package com.red_escuela.backend.notification.reporting.usecase;

import com.red_escuela.backend.notification.reporting.domain.CollectionReminderMetric;
import com.red_escuela.backend.notification.reporting.domain.MonthWindow;
import com.red_escuela.backend.notification.reporting.service.CollectionReminderMetricQuery;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.YearMonth;
import java.util.Optional;

@UseCase
public class GetCollectionReminderMetricService implements GetCollectionReminderMetricUseCase {
    static final int DEFAULT_MONTHS = 12;

    private final CollectionReminderMetricQuery metricQuery;

    public GetCollectionReminderMetricService(CollectionReminderMetricQuery metricQuery) {
        this.metricQuery = metricQuery;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public CollectionReminderMetric getCollectionReminderMetric(YearMonth from, YearMonth to) {
        YearMonth last = Optional.ofNullable(to).orElseGet(ApplicationTime::currentMonth);
        YearMonth first = Optional.ofNullable(from).orElse(last.minusMonths(DEFAULT_MONTHS - 1L));
        return metricQuery.findMetric(new MonthWindow(first, last));
    }
}
