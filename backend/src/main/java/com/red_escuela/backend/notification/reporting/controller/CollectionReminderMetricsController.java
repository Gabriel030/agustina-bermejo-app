package com.red_escuela.backend.notification.reporting.controller;

import com.red_escuela.backend.notification.reporting.dto.CollectionReminderMetricResponse;
import com.red_escuela.backend.notification.reporting.usecase.GetCollectionReminderMetricUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/notification-metrics")
@PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
public class CollectionReminderMetricsController {

    private final GetCollectionReminderMetricUseCase getCollectionReminderMetric;

    public CollectionReminderMetricsController(GetCollectionReminderMetricUseCase getCollectionReminderMetric) {
        this.getCollectionReminderMetric = getCollectionReminderMetric;
    }

    @GetMapping("/collection-reminders")
    public CollectionReminderMetricResponse getCollectionReminders(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
        return CollectionReminderMetricResponse.from(getCollectionReminderMetric.getCollectionReminderMetric(from, to));
    }
}
