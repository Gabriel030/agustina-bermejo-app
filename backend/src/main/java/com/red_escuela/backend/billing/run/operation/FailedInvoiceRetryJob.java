package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.service.FailedMonthlyInvoiceRetrier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FailedInvoiceRetryJob {
    public static final String CRON = "0 0/30 6-21 * * *";

    private final FailedMonthlyInvoiceRetrier retrier;

    public FailedInvoiceRetryJob(FailedMonthlyInvoiceRetrier retrier) {
        this.retrier = retrier;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "FailedInvoiceRetryJob", lockAtMostFor = "PT20M")
    public void run() {
        retrier.retryDueFailedItems();
    }
}
