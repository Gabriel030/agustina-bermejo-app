package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.NightlyBillingSchedule;
import com.red_escuela.backend.billing.run.service.PendingDraftReviewAlerter;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class DraftInvoicesAwaitingReviewAlertJob {
    public static final String CRON = NightlyBillingSchedule.WATCHDOG_CRON;

    private final PendingDraftReviewAlerter alerter;
    private final Clock clock;

    public DraftInvoicesAwaitingReviewAlertJob(PendingDraftReviewAlerter alerter, Clock clock) {
        this.alerter = alerter;
        this.clock = clock;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "DraftInvoicesAwaitingReviewAlertJob", lockAtMostFor = "PT10M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public int run(LocalDate businessDate) {
        return alerter.alertRunsWithDraftsStillAwaitingReview(businessDate);
    }
}
