package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.operation.MonthlyInvoiceIssuanceJob;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInvoiceRunRegistryTest extends AbstractInvoiceSequenceScenarioTest {

    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private Long enrollmentStartingIn(LocalDate firstDay) {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(firstDay);
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
    }

    private MonthlyInvoiceRun onlyRunOf(YearMonth period) {
        List<MonthlyInvoiceRun> runs = runRepository.findByPeriodOrderByIdAsc(period.atDay(1));
        assertThat(runs).hasSize(1);
        return runs.get(0);
    }

    @Test
    @Tag("D-160")
    void theFirstDayRunRecordsOneItemPerEnrollmentWithItsInvoices() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        job.completeTheNightOf(date(2026, 4, 1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        MonthlyInvoiceRun run = onlyRunOf(YearMonth.of(2026, 4));
        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(run.getPricingDate()).isEqualTo(date(2026, 4, 1));
        MonthlyInvoiceRunItem item = itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream()
                .filter(candidate -> candidate.getEnrollmentId().equals(enrollment)).findFirst().orElseThrow();
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(item.getInvoiceIds()).isNotEmpty();
        assertThat(sequence(enrollment)).isNotEmpty();
    }

    @Test
    @Tag("D-160")
    void withoutACompletedNightTheRunFailsAndIssuesNothing() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        jdbcTemplate.update("delete from nightly_billing_run where business_date = ?", date(2026, 4, 1));
        int invoicesBefore = sequence(enrollment).size();

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        MonthlyInvoiceRun run = onlyRunOf(YearMonth.of(2026, 4));
        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.FAILED);
        assertThat(run.getFailureCode()).isEqualTo("NIGHTLY_BILLING_NOT_COMPLETED");
        assertThat(itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId())).isEmpty();
        assertThat(sequence(enrollment)).hasSize(invoicesBefore);
    }

    @Test
    @Tag("D-458")
    void aSecondTriggerOfTheSamePeriodIsIgnoredAndNeverDuplicatesInvoices() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        job.completeTheNightOf(date(2026, 4, 1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
        int invoicesAfterFirst = sequence(enrollment).size();
        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).hasSize(1);
        assertThat(sequence(enrollment)).hasSize(invoicesAfterFirst);
    }

    @Test
    @Tag("D-160")
    void thereIsNoIssuanceWindowAfterTheFirstDay() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 2));
        job.completeTheNightOf(date(2026, 4, 2));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 2), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(runRepository.findByPeriodOrderByIdAsc(date(2026, 4, 1))).isEmpty();
        assertThat(sequence(enrollment)).isEmpty();
    }

    @Test
    @Tag("D-160")
    void theCronFiresOnlyOnTheFirstDayOfEachMonthAtFourInTheMorning() {
        CronExpression cron = CronExpression.parse(MonthlyInvoiceIssuanceJob.CRON);

        assertThat(cron.next(LocalDateTime.of(2027, 1, 31, 12, 0))).isEqualTo(LocalDateTime.of(2027, 2, 1, 4, 0));
        assertThat(cron.next(LocalDateTime.of(2027, 2, 1, 4, 0))).isEqualTo(LocalDateTime.of(2027, 3, 1, 4, 0));
        assertThat(cron.next(LocalDateTime.of(2027, 2, 1, 3, 59))).isEqualTo(LocalDateTime.of(2027, 2, 1, 4, 0));
    }

    @Test
    @Tag("D-160")
    void aSuspendedEnrollmentIsRecordedAsNotApplicableWithThePlanCode() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        setStanding(enrollment, EnrollmentPaymentStatus.SUSPENDED);
        job.completeTheNightOf(date(2026, 4, 1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        MonthlyInvoiceRun run = onlyRunOf(YearMonth.of(2026, 4));
        MonthlyInvoiceRunItem item = itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream()
                .filter(candidate -> candidate.getEnrollmentId().equals(enrollment)).findFirst().orElseThrow();
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.NOT_APPLICABLE);
        assertThat(item.getDecisionCode()).isEqualTo("SUSPENDED_OR_WITHDRAWN");
    }

    @Test
    @Tag("D-160")
    void theFebruaryRunUsesTheLastDayCalendarAndThePeriodOfTheMonth() {
        enrollmentStartingIn(date(2027, 2, 1));
        job.completeTheNightOf(date(2027, 2, 1));

        executor.runMonthlyInvoicesOnIssuanceDay(date(2027, 2, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(onlyRunOf(YearMonth.of(2027, 2)).getPeriod()).isEqualTo(date(2027, 2, 1));
    }
}
