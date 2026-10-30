package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceSimulator;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class MonthlyInvoiceSimulationJob {
    public static final String CRON = "0 0 4 25 * *";
    public static final String MAX_RUNNING_TIME_ISO = "PT2H";

    private final MonthlyInvoiceSimulator simulator;
    private final Clock clock;

    public MonthlyInvoiceSimulationJob(MonthlyInvoiceSimulator simulator, Clock clock) {
        this.simulator = simulator;
        this.clock = clock;
    }

    @Scheduled(cron = CRON, zone = "${app.timezone}")
    @SchedulerLock(name = "MonthlyInvoiceSimulationJob", lockAtMostFor = MAX_RUNNING_TIME_ISO)
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate businessDate) {
        YearMonth nextPeriod = YearMonth.from(businessDate).plusMonths(1);
        simulator.simulateMonthlyInvoices(nextPeriod, businessDate, nextPeriod.atDay(1), nextPeriod.atDay(1), MonthlyInvoiceRunTrigger.SCHEDULED);
    }
}
