package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.exception.DraftNotPendingException;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotAwaitingReviewException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftReviewer;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-517")
@Tag("D-518")
@Tag("D-516")
@SlowTest(reason = "Runs concurrent approvals and three lock-wait scenarios that hold row locks from another transaction while the approval waits")
class MonthlyRunDraftReviewIT extends AbstractInvoiceSequenceScenarioTest {
    private static final StatusChangeActor REVIEWER = new StatusChangeActor(null, "test-reviewer");

    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private MonthlyRunDraftReviewer reviewer;
    @Autowired
    private StudentCurrentAccountService accountService;

    private Career career;

    @BeforeEach
    @AfterEach
    void forgetRunsAndDetachTheirInvoices() {
        jdbcTemplate.update("update invoice set monthly_invoice_run_id = null where monthly_invoice_run_id is not null");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private MonthlyInvoiceRun reviewRunWithDrafts(int enrollments) {
        career = newCareer();
        var year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 1));
        for (int i = 0; i < enrollments; i++) {
            newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
        }
        job.completeTheNightOf(date(2026, 4, 1));
        return executor.runMonthlyInvoicesOfPeriodPricedAt(YearMonth.of(2026, 4), date(2026, 4, 1), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED,
                MonthlyInvoiceIssuanceMode.REVIEW).orElseThrow();
    }

    private List<Long> draftIdsOf(MonthlyInvoiceRun run) {
        return itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().flatMap(item -> item.getInvoiceIds().stream()).sorted().toList();
    }

    private String documentStatusOf(Long invoiceId) {
        return jdbcTemplate.queryForObject("select document_status from invoice where id = ?", String.class, invoiceId);
    }

    private MonthlyInvoiceRunStatus statusOf(MonthlyInvoiceRun run) {
        return runRepository.findById(run.getId()).orElseThrow().getStatus();
    }

    @Test
    void approvingTwoOfThreeDraftsIssuesThemWithNumberAndDebitAndLeavesTheThirdPending() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));

        reviewer.approveDraftsOfRun(run.getId(), ids.subList(0, 2), REVIEWER);

        ids.subList(0, 2).forEach(id -> {
            assertThat(documentStatusOf(id)).isEqualTo("ISSUED");
            assertThat(jdbcTemplate.queryForObject("select invoice_number from invoice where id = ?", String.class, id)).isNotBlank();
            assertThat(jdbcTemplate.queryForObject("select count(*) from current_account_entry where entry_type = 'INVOICE_ISSUED' and invoice_id = ?", Long.class, id)).isEqualTo(1L);
        });
        assertThat(documentStatusOf(ids.get(2))).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("select count(*) from authorization_request", Long.class)).isZero();
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);
    }

    @Test
    void anApprovedDraftKeepsTheDayOnePriceAndDuesTheConfiguredDaysAfterTheApprovalDate() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        Long id = draftIdsOf(run).get(0);
        var draftAmount = jdbcTemplate.queryForObject("select total from invoice where id = ?", java.math.BigDecimal.class, id);
        PriceFixtures.persistPriceVersion(career.programId(), null, FeeType.MONTHLY_INSTALLMENT, "Aumento", ars(12000), date(2026, 4, 3), career.directorId());
        today(date(2026, 4, 5));

        reviewer.approveDraftsOfRun(run.getId(), List.of(id), REVIEWER);

        assertThat(jdbcTemplate.queryForObject("select total from invoice where id = ?", java.math.BigDecimal.class, id)).isEqualByComparingTo(draftAmount);
        assertThat(jdbcTemplate.queryForObject("select issue_date from invoice where id = ?", java.sql.Date.class, id).toLocalDate()).isEqualTo(date(2026, 4, 5));
        assertThat(jdbcTemplate.queryForObject("select due_date from invoice where id = ?", java.sql.Date.class, id).toLocalDate()).isEqualTo(date(2026, 4, 15));
    }

    @Test
    void theDueDateIsNeverBeforeTheIssueDateWhateverTheApprovalDay() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<LocalDate> approvalDays = List.of(date(2026, 4, 1), date(2026, 4, 5), date(2026, 4, 28));
        List<Long> ids = draftIdsOf(run);
        for (int i = 0; i < approvalDays.size(); i++) {
            today(approvalDays.get(i));

            reviewer.approveDraftsOfRun(run.getId(), List.of(ids.get(i)), REVIEWER);

            assertThat(jdbcTemplate.queryForObject("select due_date >= issue_date from invoice where id = ?", Boolean.class, ids.get(i))).isTrue();
        }
    }

    @Test
    void discardingADraftReleasesItsChargeKeyAndTheRunCompletesWhenNoDraftsRemain() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(2);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));

        reviewer.approveDraftsOfRun(run.getId(), ids.subList(0, 1), REVIEWER);
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);
        reviewer.discardDraftsOfRun(run.getId(), ids.subList(1, 2), "Cargo duplicado", REVIEWER);

        assertThat(documentStatusOf(ids.get(1))).isEqualTo("DISCARDED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Long.class, ids.get(1))).isEqualTo(1L);
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }

    @Test
    void aBatchWithAnAlreadyDiscardedDraftFailsAsAWholeWithoutIssuingAny() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        reviewer.discardDraftsOfRun(run.getId(), ids.subList(2, 3), "Error", REVIEWER);

        assertThatThrownBy(() -> reviewer.approveDraftsOfRun(run.getId(), ids, REVIEWER)).isInstanceOf(DraftNotPendingException.class);

        assertThat(ids.subList(0, 2)).allMatch(id -> "DRAFT".equals(documentStatusOf(id)));
    }

    @Test
    void aFinishedRunDoesNotAcceptReviewAndInvoicesOfAnotherRunAreNotPending() {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        List<Long> ids = draftIdsOf(run);
        reviewer.discardDraftsOfRun(run.getId(), ids, "Error", REVIEWER);

        assertThatThrownBy(() -> reviewer.approveDraftsOfRun(run.getId(), ids, REVIEWER)).isInstanceOf(MonthlyInvoiceRunNotAwaitingReviewException.class);
    }

    private List<Object> runTogether(List<Callable<Object>> tasks, CountDownLatch start) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(pool.submit(task)));
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Object> approving(Long runId, List<Long> ids, CountDownLatch start) {
        return () -> {
            start.await();
            return reviewer.approveDraftsOfRun(runId, ids, REVIEWER);
        };
    }

    @Test
    void twoThreadsApprovingTheSameBatchIssueEachDraftOnce() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runTogether(List.of(approving(run.getId(), ids, start), approving(run.getId(), ids, start)), start);

        assertThat(outcomes.stream().filter(outcome -> !(outcome instanceof Throwable))).as(outcomes.toString()).hasSize(1);
        assertThat(outcomes.stream().filter(DraftNotPendingException.class::isInstance).count() + outcomes.stream().filter(MonthlyInvoiceRunNotAwaitingReviewException.class::isInstance).count()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(distinct invoice_number) from invoice where id in (" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")", Long.class)).isEqualTo((long) ids.size());
        assertThat(jdbcTemplate.queryForObject("select count(*) from current_account_entry where entry_type = 'INVOICE_ISSUED' and invoice_id in (" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")", Long.class)).isEqualTo((long) ids.size());
    }

    @Test
    void approvingWhileAnotherThreadDiscardsOneOfTheIdsNeverLeavesAPartialBatch() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> discarding = () -> {
            start.await();
            return reviewer.discardDraftsOfRun(run.getId(), ids.subList(2, 3), "Error", REVIEWER);
        };

        List<Object> outcomes = runTogether(List.of(approving(run.getId(), ids, start), discarding), start);

        String approvedStatus = documentStatusOf(ids.get(0));
        if (outcomes.get(0) instanceof Throwable) {
            assertThat(outcomes.get(0)).isInstanceOf(DraftNotPendingException.class);
            assertThat(approvedStatus).isEqualTo("DRAFT");
            assertThat(documentStatusOf(ids.get(1))).isEqualTo("DRAFT");
            assertThat(documentStatusOf(ids.get(2))).isEqualTo("DISCARDED");
        } else {
            assertThat(outcomes.get(1)).isInstanceOfAny(DraftNotPendingException.class, MonthlyInvoiceRunNotAwaitingReviewException.class);
            assertThat(ids).allMatch(id -> "ISSUED".equals(documentStatusOf(id)));
        }
    }

    @Test
    void approvingWhileAPaymentIsRecordedOnTheSameAccountFinishesWithoutDeadlock() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        Long id = draftIdsOf(run).get(0);
        Long accountId = jdbcTemplate.queryForObject("select student_current_account_id from invoice where id = ?", Long.class, id);
        today(date(2026, 4, 5));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> paying = () -> {
            start.await();
            NonNegativeMoney payment = NonNegativeMoney.of(ars("5000.00"));
            return transactionTemplate.execute(status -> {
                var account = accountService.lockStudentCurrentAccount(accountId);
                accountService.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(accountId, payment), payment, date(2026, 4, 5), "Pago");
                return null;
            });
        };

        List<Object> outcomes = runTogether(List.of(approving(run.getId(), List.of(id), start), paying), start);

        assertThat(outcomes).as(outcomes.toString()).noneMatch(outcome -> outcome instanceof Throwable);
        assertThat(documentStatusOf(id)).isEqualTo("ISSUED");
    }

    private void requireApprovalToWaitWhileRowIsLocked(String lockSql) throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        Long id = draftIdsOf(run).get(0);
        today(date(2026, 4, 5));
        Long accountId = jdbcTemplate.queryForObject("select student_current_account_id from invoice where id = ?", Long.class, id);
        Long target = lockSql.contains("monthly_invoice_run") ? run.getId() : lockSql.contains("invoice where") ? id : accountId;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holderPool = Executors.newSingleThreadExecutor();
        Future<?> holder = holderPool.submit(() -> transactionTemplate.execute(status -> {
            jdbcTemplate.queryForList(lockSql, target);
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        try {
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Object> approval = pool.submit(() -> reviewer.approveDraftsOfRun(run.getId(), List.of(id), REVIEWER));
            org.awaitility.Awaitility.await().during(java.time.Duration.ofMillis(1000)).atMost(java.time.Duration.ofSeconds(10)).untilAsserted(
                    () -> assertThat(approval.isDone()).as("approval must wait for the lock held by another transaction").isFalse());
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            approval.get(30, TimeUnit.SECONDS);
            assertThat(documentStatusOf(id)).isEqualTo("ISSUED");
        } finally {
            release.countDown();
            pool.shutdownNow();
            holderPool.shutdownNow();
        }
    }

    @Test
    void approvalWaitsForTheLockOfTheRun() throws Exception {
        requireApprovalToWaitWhileRowIsLocked("select id from monthly_invoice_run where id = ? for update");
    }

    @Test
    void approvalWaitsForTheLockOfTheDraft() throws Exception {
        requireApprovalToWaitWhileRowIsLocked("select id from invoice where id = ? for update");
    }

    @Test
    void approvalWaitsForTheLockOfTheAccount() throws Exception {
        requireApprovalToWaitWhileRowIsLocked("select id from student_current_account where id = ? for update");
    }
}
