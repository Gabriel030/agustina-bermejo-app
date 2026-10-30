package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class MonthlyInvoiceIssuanceJob {
    public static final String CRON = "0 0 4 1 * *";
    private static final int ISSUANCE_DAY = 1;

    private final MonthlyInvoiceRunExecutor executor;
    private final Clock clock;

    public MonthlyInvoiceIssuanceJob(MonthlyInvoiceRunExecutor executor, Clock clock) {
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "MonthlyInvoiceIssuanceJob", lockAtMostFor = MonthlyInvoiceRun.MAX_RUNNING_TIME_ISO)
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate businessDate) {
        if (businessDate.getDayOfMonth() == ISSUANCE_DAY) {
            executor.runMonthlyInvoicesOfBusinessDate(businessDate, MonthlyInvoiceRunTrigger.SCHEDULED);
        }
    }
}
