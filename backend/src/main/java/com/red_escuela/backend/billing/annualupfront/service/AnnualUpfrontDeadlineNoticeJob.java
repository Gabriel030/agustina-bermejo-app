package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnnualUpfrontDeadlineNoticeJob {
    private final AnnualUpfrontDeadlineNoticePlanner planner;

    public AnnualUpfrontDeadlineNoticeJob(AnnualUpfrontDeadlineNoticePlanner planner) {
        this.planner = planner;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = "0 0 * * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "AnnualUpfrontDeadlineNoticeJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void planDeadlineNotices() {
        planner.planDeadlineNotices();
    }
}
