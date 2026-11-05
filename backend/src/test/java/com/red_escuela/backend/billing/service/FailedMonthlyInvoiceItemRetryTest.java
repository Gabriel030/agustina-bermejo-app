package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemAttempt;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemAttemptRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.FailedMonthlyInvoiceItemRetryProcessor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailedMonthlyInvoiceItemRetryTest extends AbstractInvoiceSequenceScenarioTest {

    @Autowired
    private FailedMonthlyInvoiceItemRetryProcessor retryProcessor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private MonthlyInvoiceRunItemAttemptRepository attemptRepository;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private MonthlyInvoiceRunItem failedItemOfFirstDayRun(Long enrollmentId) {
        LocalDate firstDay = date(2026, 4, 1);
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startRealDirectRun(firstDay, firstDay, MonthlyInvoiceRunTrigger.SCHEDULED, firstDay.atTime(4, 0));
        run.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES, firstDay.atTime(4, 5));
        run = runRepository.save(run);
        return itemRepository.save(MonthlyInvoiceRunItem.recordItemOutcome(run.getId(), MonthlyInvoiceEnrollmentType.PROGRAM, enrollmentId,
                MonthlyInvoiceItemOutcome.failed("Boom", "boom"), firstDay.atTime(4, 0), new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24))));
    }

    private Long enrollmentStartingIn(LocalDate firstDay) {
        var career = newCareer();
        var year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(firstDay);
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
    }

    @Test
    @Tag("D-054")
    void aRetryOnTheFourthDayIssuesTheFirstDayInstallmentOnceAndTheNextRetryCannotDuplicateIt() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        MonthlyInvoiceRunItem failed = failedItemOfFirstDayRun(enrollment);
        int invoicesBefore = sequence(enrollment).size();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 4, 4, 10, 0));

        MonthlyInvoiceRunItem retried = retryProcessor.retryItemIfDue(failed.getId()).orElseThrow();
        int invoicesAfterRetry = sequence(enrollment).size();
        boolean secondRetryTookTheItem = retryProcessor.retryItemIfDue(failed.getId()).isPresent();

        assertThat(retried.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
        assertThat(retried.getAttempts()).isEqualTo(2);
        assertThat(retried.getInvoiceIds()).isNotEmpty();
        assertThat(invoicesAfterRetry).isGreaterThan(invoicesBefore);
        assertThat(secondRetryTookTheItem).isFalse();
        assertThat(sequence(enrollment)).hasSize(invoicesAfterRetry);
        List<MonthlyInvoiceRunItemAttempt> attempts = attemptRepository.findByMonthlyInvoiceRunItemIdOrderByAttemptNumberAsc(failed.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getAttemptNumber()).isEqualTo(2);
        assertThat(attempts.get(0).getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);
    }

    @Test
    @Tag("D-160")
    void aRetryBeforeTheBackoffElapsedLeavesTheItemUntouchedAndIssuesNothing() {
        Long enrollment = enrollmentStartingIn(date(2026, 4, 1));
        MonthlyInvoiceRunItem failed = failedItemOfFirstDayRun(enrollment);
        int invoicesBefore = sequence(enrollment).size();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 4, 1, 4, 30));

        assertThat(retryProcessor.retryItemIfDue(failed.getId())).isEmpty();

        assertThat(itemRepository.findById(failed.getId()).orElseThrow().getResult()).isEqualTo(MonthlyInvoiceRunItemResult.FAILED);
        assertThat(sequence(enrollment)).hasSize(invoicesBefore);
    }
}
