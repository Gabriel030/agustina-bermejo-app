package com.red_escuela.backend.billing.payment.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueReceiptReviewAlertJob {
    private final OverdueReceiptReviewAlerter alerter;

    public OverdueReceiptReviewAlertJob(OverdueReceiptReviewAlerter alerter) {
        this.alerter = alerter;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "OverdueReceiptReviewAlertJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void alertOverdueReviews() {
        alerter.alertOverdueReviews();
    }
}
