package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.dto.RunPage;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunItemNotFoundException;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.exception.RunItemNotRetryableException;
import com.red_escuela.backend.billing.run.usecase.ManualMonthlyInvoiceItemRetryUseCase;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunQueryService;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyInvoiceRunApiTest extends AbstractInvoiceSequenceScenarioTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Autowired
    private MonthlyInvoiceRunQueryUseCase queryUseCase;
    @Autowired
    private ManualMonthlyInvoiceItemRetryUseCase manualRetryUseCase;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private Long insertRun(YearMonth period, String status) {
        return jdbcTemplate.queryForObject("""
                insert into monthly_invoice_run (period, mode, issuance_mode, business_date, pricing_date, status, trigger, started_at, finished_at)
                values (?, 'REAL', 'DIRECT', ?, ?, ?, 'SCHEDULED', ?, ?) returning id
                """, Long.class, period.atDay(1), period.atDay(1), period.atDay(1), status, period.atDay(1).atTime(4, 0), period.atDay(1).atTime(4, 30));
    }

    private Long insertItem(Long runId, long enrollmentId, String result, int attempts) {
        return jdbcTemplate.queryForObject("""
                insert into monthly_invoice_run_item (monthly_invoice_run_id, enrollment_type, enrollment_id, result, attempts, error_code, message)
                values (?, 'PROGRAM', ?, ?, ?, 'E', 'boom') returning id
                """, Long.class, runId, enrollmentId, result, attempts);
    }

    private Long enrollmentWithoutPriceIssuedYet() {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 10, 1));
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, OCTOBER, date(2026, 3, 10));
    }

    private int attemptsRecordedFor(Long itemId) {
        return jdbcTemplate.queryForObject("select count(*) from monthly_invoice_run_item_attempt where monthly_invoice_run_item_id = ?", Integer.class, itemId);
    }

    @Test
    @Tag("D-160")
    @Tag("D-054")
    void manuallyRetryingAnExhaustedItemOnTheFourthDayIssuesItAndRecordsTheNextAttempt() {
        Long enrollment = enrollmentWithoutPriceIssuedYet();
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        Long itemId = insertItem(runId, enrollment, "RETRIES_EXHAUSTED", 6);
        today(date(2026, 10, 4));

        MonthlyInvoiceRunItemResponse retried = manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, itemId);

        assertThat(retried.result()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(retried.attempts()).isEqualTo(7);
        assertThat(retried.invoiceIds()).isNotEmpty();
        assertThat(sequence(enrollment)).isNotEmpty();
        assertThat(jdbcTemplate.queryForObject("select max(attempt_number) from monthly_invoice_run_item_attempt where monthly_invoice_run_item_id = ?", Integer.class, itemId))
                .isEqualTo(7);
    }

    @Test
    @Tag("D-160")
    void retryingAnItemThatAlreadyClosedAsIssuedIsAConflictAndEmitsNothing() {
        Long enrollment = enrollmentWithoutPriceIssuedYet();
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        Long itemId = insertItem(runId, enrollment, "FAILED", 1);
        manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, itemId);
        int invoicesAfterFirstRetry = sequence(enrollment).size();

        assertThatThrownBy(() -> manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, itemId)).isInstanceOf(RunItemNotRetryableException.class);

        assertThat(sequence(enrollment)).hasSize(invoicesAfterFirstRetry);
        assertThat(attemptsRecordedFor(itemId)).isEqualTo(1);
    }

    @Test
    @Tag("D-160")
    void twoSimultaneousClicksOnTheSameItemEmitASingleInvoice() throws Exception {
        Long enrollment = enrollmentWithoutPriceIssuedYet();
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        Long itemId = insertItem(runId, enrollment, "RETRIES_EXHAUSTED", 6);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> clicks = List.of(1, 2).stream().map(click -> pool.submit(() -> {
                go.await();
                try {
                    manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, itemId);
                    return true;
                } catch (RunItemNotRetryableException e) {
                    return false;
                }
            })).toList();
            go.countDown();
            long succeeded = 0;
            for (Future<Boolean> click : clicks) {
                succeeded += click.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(attemptsRecordedFor(itemId)).isEqualTo(1);
            assertThat(sequence(enrollment)).isNotEmpty();
            assertThat(jdbcTemplate.queryForObject("select attempts from monthly_invoice_run_item where id = ?", Integer.class, itemId)).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Tag("D-160")
    void anItemOfAnotherRunOrAnUnknownOneIsNotFound() {
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        Long otherRunId = insertRun(YearMonth.of(2026, 9), "COMPLETED_WITH_FAILURES");
        Long itemOfOtherRun = insertItem(otherRunId, 11L, "FAILED", 1);

        assertThatThrownBy(() -> manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, itemOfOtherRun)).isInstanceOf(MonthlyInvoiceRunItemNotFoundException.class);
        assertThatThrownBy(() -> manualRetryUseCase.retryMonthlyInvoiceRunItemManually(runId, -1L)).isInstanceOf(MonthlyInvoiceRunItemNotFoundException.class);
        assertThat(attemptsRecordedFor(itemOfOtherRun)).isZero();
    }

    @Test
    @Tag("D-160")
    void theRunDetailCountsItemsByResultAndTheResultFilterMatchesTheCounters() {
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        Long otherRunId = insertRun(YearMonth.of(2026, 9), "COMPLETED");
        long enrollmentId = 100;
        for (String result : List.of("ISSUED", "ISSUED", "ISSUED", "NOT_APPLICABLE", "ALREADY_ISSUED", "ALREADY_ISSUED", "FAILED", "FAILED", "RETRIES_EXHAUSTED")) {
            insertItem(runId, enrollmentId++, result, 0);
        }
        insertItem(otherRunId, enrollmentId, "FAILED", 1);

        MonthlyInvoiceRunResponse detail = queryUseCase.findMonthlyInvoiceRun(runId);

        assertThat(detail.counters().issued()).isEqualTo(3);
        assertThat(detail.counters().notApplicable()).isEqualTo(1);
        assertThat(detail.counters().alreadyIssued()).isEqualTo(2);
        assertThat(detail.counters().failed()).isEqualTo(2);
        assertThat(detail.counters().retriesExhausted()).isEqualTo(1);
        RunPage<MonthlyInvoiceRunItemResponse> failed = queryUseCase.listMonthlyInvoiceRunItems(runId, MonthlyInvoiceRunItemResult.FAILED, null, null);
        assertThat(failed.totalElements()).isEqualTo(detail.counters().failed());
        assertThat(failed.items()).extracting(MonthlyInvoiceRunItemResponse::result).containsOnly(MonthlyInvoiceRunItemResult.FAILED);
        assertThatThrownBy(() -> queryUseCase.findMonthlyInvoiceRun(-1L)).isInstanceOf(MonthlyInvoiceRunNotFoundException.class);
    }

    @Test
    @Tag("D-160")
    void theRunListIsNewestFirstFilterableByPeriodAndCarriesItsCounters() {
        Long september = insertRun(YearMonth.of(2026, 9), "COMPLETED");
        Long october = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        insertItem(october, 1L, "FAILED", 1);

        RunPage<MonthlyInvoiceRunResponse> all = queryUseCase.listMonthlyInvoiceRuns(null, null, null);
        RunPage<MonthlyInvoiceRunResponse> onlyOctober = queryUseCase.listMonthlyInvoiceRuns(OCTOBER, null, null);

        assertThat(all.items()).extracting(MonthlyInvoiceRunResponse::id).containsExactly(october, september);
        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(onlyOctober.items()).extracting(MonthlyInvoiceRunResponse::id).containsExactly(october);
        assertThat(onlyOctober.items().get(0).counters().failed()).isEqualTo(1);
        assertThat(onlyOctober.items().get(0).period()).isEqualTo(OCTOBER.atDay(1));
    }

    @Test
    @Tag("D-160")
    void itemPagesAreStableAscendingByIdAndTheSizeIsCapped() {
        Long runId = insertRun(OCTOBER, "COMPLETED_WITH_FAILURES");
        jdbcTemplate.update("insert into monthly_invoice_run_item (monthly_invoice_run_id, enrollment_type, enrollment_id, result) "
                + "select ?, 'COURSE', g, 'ISSUED' from generate_series(1, 205) g", runId);

        RunPage<MonthlyInvoiceRunItemResponse> first = queryUseCase.listMonthlyInvoiceRunItems(runId, null, 0, 2);
        RunPage<MonthlyInvoiceRunItemResponse> second = queryUseCase.listMonthlyInvoiceRunItems(runId, null, 1, 2);
        RunPage<MonthlyInvoiceRunItemResponse> capped = queryUseCase.listMonthlyInvoiceRunItems(runId, null, 0, 100_000);
        RunPage<MonthlyInvoiceRunItemResponse> defaultSize = queryUseCase.listMonthlyInvoiceRunItems(runId, null, null, null);
        RunPage<MonthlyInvoiceRunItemResponse> lastPage = queryUseCase.listMonthlyInvoiceRunItems(runId, null, 1, MonthlyInvoiceRunQueryService.MAX_PAGE_SIZE);

        assertThat(first.totalElements()).isEqualTo(205);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items().get(0).id()).isGreaterThan(first.items().get(1).id());
        assertThat(second.items().get(0).enrollmentId()).isEqualTo(first.items().get(1).enrollmentId() + 1);
        assertThat(capped.items()).hasSize(MonthlyInvoiceRunQueryService.MAX_PAGE_SIZE);
        assertThat(defaultSize.items()).hasSize(MonthlyInvoiceRunQueryService.DEFAULT_PAGE_SIZE);
        assertThat(lastPage.items()).hasSize(5);
    }
}
