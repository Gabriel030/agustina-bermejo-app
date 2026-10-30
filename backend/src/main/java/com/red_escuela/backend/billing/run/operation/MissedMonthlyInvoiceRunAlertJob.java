package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.NightlyBillingSchedule;
import com.red_escuela.backend.billing.run.service.MissedMonthlyInvoiceRunDetector;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class MissedMonthlyInvoiceRunAlertJob {
    public static final String CRON = NightlyBillingSchedule.WATCHDOG_CRON;

    private final MissedMonthlyInvoiceRunDetector detector;
    private final Clock clock;

    public MissedMonthlyInvoiceRunAlertJob(MissedMonthlyInvoiceRunDetector detector, Clock clock) {
        this.detector = detector;
        this.clock = clock;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "MissedMonthlyInvoiceRunAlertJob", lockAtMostFor = "PT10M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate businessDate) {
        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(businessDate);
    }
}
