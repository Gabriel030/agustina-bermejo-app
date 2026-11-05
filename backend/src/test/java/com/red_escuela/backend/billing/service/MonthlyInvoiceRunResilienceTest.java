package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInvoiceRunResilienceTest extends AbstractInvoiceSequenceScenarioTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private Clock clock;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private Long enrollmentStartingOnAprilFirst() {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 1));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, APRIL, date(2026, 3, 10));
        job.completeTheNightOf(date(2026, 4, 1));
        return enrollment;
    }

    private void insertRunningRunStartedAt(LocalDateTime startedAt) {
        jdbcTemplate.update("""
                insert into monthly_invoice_run (period, mode, issuance_mode, business_date, pricing_date, status, trigger, started_at)
                values (?, 'REAL', 'DIRECT', ?, ?, 'RUNNING', 'SCHEDULED', ?)
                """, date(2026, 4, 1), date(2026, 4, 1), date(2026, 4, 1), startedAt);
    }

    @Test
    @Tag("D-160")
    void aRunningRunOlderThanTheLockLimitIsInterruptedAndThePeriodIsIssued() {
        Long enrollment = enrollmentStartingOnAprilFirst();
        insertRunningRunStartedAt(LocalDateTime.now(clock).minusHours(3));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        List<MonthlyInvoiceRun> runs = runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1));
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).getStatus()).isEqualTo(MonthlyInvoiceRunStatus.FAILED);
        assertThat(runs.get(0).getFailureCode()).isEqualTo("INTERRUPTED");
        assertThat(runs.get(0).getFinishedAt()).isNotNull();
        assertThat(runs.get(1).getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(sequence(enrollment)).isNotEmpty();
    }

    @Test
    @Tag("D-160")
    void aRunningRunStillWithinTheLockLimitBlocksTheNewAttemptAndIssuesNothing() {
        Long enrollment = enrollmentStartingOnAprilFirst();
        insertRunningRunStartedAt(LocalDateTime.now(clock).minusHours(1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        List<MonthlyInvoiceRun> runs = runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1));
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo(MonthlyInvoiceRunStatus.RUNNING);
        assertThat(sequence(enrollment)).isEmpty();
    }

    @Test
    @Tag("D-160")
    void aRunningRunJustInsideTheLockLimitIsKept() {
        Long enrollment = enrollmentStartingOnAprilFirst();
        insertRunningRunStartedAt(LocalDateTime.now(clock).minus(Duration.ofHours(2)).plusSeconds(30));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).hasSize(1);
        assertThat(sequence(enrollment)).isEmpty();
    }

    @Test
    @Tag("D-160")
    void aRunningRunJustOutsideTheLockLimitIsInterrupted() {
        Long enrollment = enrollmentStartingOnAprilFirst();
        insertRunningRunStartedAt(LocalDateTime.now(clock).minus(Duration.ofHours(2)).minusSeconds(30));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).hasSize(2);
        assertThat(sequence(enrollment)).isNotEmpty();
    }

    @Test
    @Tag("D-458")
    void thirtyConcurrentExecutionsOfTheSamePeriodCreateOneRunAndNeverDuplicateInvoices() throws Exception {
        Long enrollment = enrollmentStartingOnAprilFirst();

        int started = runConcurrently(30);

        assertThat(started).isEqualTo(1);
        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).hasSize(1);
        List<String> invoiceIds = sequence(enrollment);
        assertThat(invoiceIds).isNotEmpty().doesNotHaveDuplicates();
        executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
        assertThat(sequence(enrollment)).hasSameSizeAs(invoiceIds);
    }

    @Test
    @Tag("D-458")
    void twoNodesTriggeringTheSamePeriodAtTheSameTimeStartExactlyOneRun() throws Exception {
        Long enrollment = enrollmentStartingOnAprilFirst();

        int started = runConcurrently(2);

        assertThat(started).isEqualTo(1);
        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).hasSize(1);
        assertThat(sequence(enrollment)).isNotEmpty();
    }

    @Test
    @Tag("D-160")
    void aRunInterruptedAfterIssuingInvoicesIsReplacedWithoutDuplicatingThem() {
        Long enrollment = enrollmentStartingOnAprilFirst();
        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
        int issuedByTheFirstRun = sequence(enrollment).size();
        jdbcTemplate.update("update monthly_invoice_run set status = 'RUNNING', finished_at = null, failure_code = null, started_at = ? where period = ?",
                LocalDateTime.now(clock).minusHours(3), date(2026, 4, 1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        List<MonthlyInvoiceRun> runs = runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1));
        assertThat(runs).extracting(MonthlyInvoiceRun::getStatus).containsExactly(MonthlyInvoiceRunStatus.FAILED, MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(sequence(enrollment)).hasSize(issuedByTheFirstRun);
    }

    private int runConcurrently(int executions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(executions);
        CountDownLatch ready = new CountDownLatch(executions);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<MonthlyInvoiceRun>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < executions; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
                }));
            }
            ready.await();
            go.countDown();
            int started = 0;
            for (Future<Optional<MonthlyInvoiceRun>> future : futures) {
                if (future.get(120, TimeUnit.SECONDS).isPresent()) {
                    started++;
                }
            }
            return started;
        } finally {
            pool.shutdownNow();
        }
    }
}
