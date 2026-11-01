package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonthlyInvoiceIssuanceJob {
    public static final String CRON = "0 0 4 1 * *";

    private final MonthlyInvoiceRunExecutor executor;

    public MonthlyInvoiceIssuanceJob(MonthlyInvoiceRunExecutor executor) {
        this.executor = executor;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "MonthlyInvoiceIssuanceJob", lockAtMostFor = MonthlyInvoiceRun.MAX_RUNNING_TIME_ISO)
    public void run() {
        executor.runMonthlyInvoicesOnIssuanceDayOfToday(MonthlyInvoiceRunTrigger.SCHEDULED);
    }
}
