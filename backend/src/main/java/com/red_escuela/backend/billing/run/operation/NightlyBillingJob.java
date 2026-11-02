package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.run.domain.NightlyBillingSchedule;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class NightlyBillingJob {
    public static final String CRON = NightlyBillingSchedule.NIGHTLY_CRON;

    private final NightlyBillingRunner runner;
    private final Clock clock;

    public NightlyBillingJob(NightlyBillingRunner runner, Clock clock) {
        this.runner = runner;
        this.clock = clock;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "NightlyBillingJob", lockAtMostFor = "PT2H")
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate businessDate) {
        runner.runNightlyBillingSteps(businessDate, NightlyBillingTrigger.SCHEDULED);
    }
}
