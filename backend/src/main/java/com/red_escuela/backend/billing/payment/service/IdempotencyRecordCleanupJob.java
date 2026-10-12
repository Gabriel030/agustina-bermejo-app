package com.red_escuela.backend.billing.payment.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRecordCleanupJob {
    private final IdempotencyRecordService recordService;

    public IdempotencyRecordCleanupJob(IdempotencyRecordService recordService) {
        this.recordService = recordService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "IdempotencyRecordCleanupJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void deleteExpiredRecords() {
        recordService.deleteRecordsOlderThanRetention();
    }
}
