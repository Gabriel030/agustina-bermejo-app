package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

public class MonthlyInvoiceRunTestDriver {
    private final MonthlyInvoiceRunExecutor executor;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MonthlyInvoiceRunTestDriver(MonthlyInvoiceRunExecutor executor, JdbcTemplate jdbc, Clock clock) {
        this.executor = executor;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void run() {
        run(YearMonth.now(clock));
    }

    public Optional<MonthlyInvoiceRun> run(YearMonth period) {
        LocalDate today = LocalDate.now(clock);
        completeTheNightOf(today);
        forgetRunsOf(period);
        return executor.runMonthlyInvoicesOfPeriod(period, today, MonthlyInvoiceRunTrigger.SCHEDULED);
    }

    public Optional<MonthlyInvoiceRun> runWithoutForgettingPreviousRuns(YearMonth period) {
        LocalDate today = LocalDate.now(clock);
        completeTheNightOf(today);
        return executor.runMonthlyInvoicesOfPeriod(period, today, MonthlyInvoiceRunTrigger.SCHEDULED);
    }

    public void completeTheNightOf(LocalDate businessDate) {
        jdbc.update("""
                insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at)
                values (?, 'COMPLETED', 'SCHEDULED', ?, ?) on conflict (business_date) do update set status = 'COMPLETED', finished_at = excluded.finished_at
                """, businessDate, LocalDateTime.now(clock), LocalDateTime.now(clock));
    }

    private void forgetRunsOf(YearMonth period) {
        jdbc.update("delete from monthly_invoice_run_item where monthly_invoice_run_id in (select id from monthly_invoice_run where period = ?)", period.atDay(1));
        jdbc.update("delete from monthly_invoice_run where period = ?", period.atDay(1));
    }
}
