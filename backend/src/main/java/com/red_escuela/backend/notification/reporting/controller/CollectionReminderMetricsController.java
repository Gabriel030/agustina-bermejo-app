package com.red_escuela.backend.notification.reporting.controller;

import com.red_escuela.backend.notification.reporting.dto.CollectionReminderMetricResponse;
import com.red_escuela.backend.notification.reporting.usecase.GetCollectionReminderMetricUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/notification-metrics")
public class CollectionReminderMetricsController {

    private final GetCollectionReminderMetricUseCase getCollectionReminderMetric;

    public CollectionReminderMetricsController(GetCollectionReminderMetricUseCase getCollectionReminderMetric) {
        this.getCollectionReminderMetric = getCollectionReminderMetric;
    }

    @RequiresPermission(code = "notification.read_collection_reminder_metrics", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping("/collection-reminders")
    public CollectionReminderMetricResponse getCollectionReminders(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
        return CollectionReminderMetricResponse.from(getCollectionReminderMetric.getCollectionReminderMetric(from, to));
    }
}
