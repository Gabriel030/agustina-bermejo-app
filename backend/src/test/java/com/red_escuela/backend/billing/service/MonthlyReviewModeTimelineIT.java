package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.events.DraftInvoicesAwaitingReviewEvent;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.operation.DraftInvoicesAwaitingReviewAlertJob;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftReviewer;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-512")
@Tag("D-519")
@Tag("D-455")
@Tag("D-452")
@RecordApplicationEvents
class MonthlyReviewModeTimelineIT extends AbstractInvoiceSequenceScenarioTest {
    private static final StatusChangeActor REVIEWER = new StatusChangeActor(null, "test-reviewer");
    private static final String CONFIGURATION_MARKER = "MonthlyReviewModeTimelineIT";

    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private MonthlyRunDraftReviewer reviewer;
    @Autowired
    private DraftInvoicesAwaitingReviewAlertJob alertJob;
    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    @AfterEach
    void forgetRunsAndConfigurations() {
        jdbcTemplate.update("update invoice set monthly_invoice_run_id = null where monthly_invoice_run_id is not null");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
        jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
        try {
            jdbcTemplate.update("update configuration_version set previous_version_id = null where change_reason = ?", CONFIGURATION_MARKER);
            jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where change_reason = ?)",
                    CONFIGURATION_MARKER);
            jdbcTemplate.update("delete from configuration_version where change_reason = ?", CONFIGURATION_MARKER);
        } finally {
            jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        }
    }

    private void configureIssuanceMode(String mode, LocalDate effectiveFrom, Career career) {
        jdbcTemplate.update("""
                insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at,
                                                   direct_publication)
                values ('ISSUANCE', 'ISSUANCE', ?::jsonb, 'EFFECTIVE', ?, ?, ?, now(), ?, now(), true)
                """, "{\"type\":\"ISSUANCE\",\"issuanceMode\":\"" + mode + "\",\"continueIssuingWhileOverdue\":true,\"maxRetryAttempts\":5,\"retryBackoffHours\":[1,3,6,12,24]}",
                effectiveFrom, CONFIGURATION_MARKER, career.directorId(), career.directorId());
    }

    private Career careerWithEnrollments(int enrollments) {
        Career career = newCareer();
        var year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 5, 1));
        for (int i = 0; i < enrollments; i++) {
            newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
        }
        return career;
    }

    private MonthlyInvoiceRun runTheFirstOf(LocalDate firstDay) {
        today(firstDay);
        job.completeTheNightOf(firstDay);
        return executor.runMonthlyInvoicesOfPeriod(YearMonth.from(firstDay), firstDay, MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();
    }

    private MonthlyInvoiceRunStatus statusOf(MonthlyInvoiceRun run) {
        return runRepository.findById(run.getId()).orElseThrow().getStatus();
    }

    private List<Long> invoiceIdsOf(MonthlyInvoiceRun run) {
        return itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().flatMap(item -> item.getInvoiceIds().stream()).sorted().toList();
    }

    private long alertsOf(MonthlyInvoiceRun run, LocalDate day) {
        return events.stream(DraftInvoicesAwaitingReviewEvent.class)
                .filter(event -> event.monthlyInvoiceRunId().equals(run.getId()) && event.alertDate().equals(day)).count();
    }

    private long alertsOf(MonthlyInvoiceRun run) {
        return events.stream(DraftInvoicesAwaitingReviewEvent.class).filter(event -> event.monthlyInvoiceRunId().equals(run.getId())).count();
    }

    private long countOf(String documentStatus, MonthlyInvoiceRun run) {
        return invoiceIdsOf(run).stream()
                .filter(id -> documentStatus.equals(jdbcTemplate.queryForObject("select document_status from invoice where id = ?", String.class, id))).count();
    }

    @Test
    void reviewConfigurationLeavesDraftsOnDayOneAndNothingReachesTheStudent() {
        Career career = careerWithEnrollments(2);
        configureIssuanceMode("REVIEW", date(2026, 1, 1), career);

        MonthlyInvoiceRun run = runTheFirstOf(date(2026, 5, 1));

        assertThat(run.getIssuanceMode()).isEqualTo(MonthlyInvoiceIssuanceMode.REVIEW);
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);
        assertThat(countOf("DRAFT", run)).isEqualTo(2);
        assertThat(countOf("ISSUED", run)).isZero();
    }

    @Test
    void directConfigurationEmitsOnDayOneAndNeverAlertsOnDayFive() {
        Career career = careerWithEnrollments(2);
        configureIssuanceMode("DIRECT", date(2026, 1, 1), career);

        MonthlyInvoiceRun run = runTheFirstOf(date(2026, 5, 1));
        alertJob.run(date(2026, 5, 5));

        assertThat(run.getIssuanceMode()).isEqualTo(MonthlyInvoiceIssuanceMode.DIRECT);
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(countOf("ISSUED", run)).isEqualTo(2);
        assertThat(alertsOf(run)).isZero();
    }

    @Test
    void noAlertBeforeDayFiveOneAlertPerDayFromDayFiveAndNoneOnWeekends() {
        Career career = careerWithEnrollments(2);
        configureIssuanceMode("REVIEW", date(2026, 1, 1), career);
        MonthlyInvoiceRun run = runTheFirstOf(date(2026, 5, 1));

        for (int day = 1; day <= 4; day++) {
            today(date(2026, 5, day));
            assertThat(alertJob.run(date(2026, 5, day))).isZero();
        }
        assertThat(alertsOf(run)).isZero();

        today(date(2026, 5, 5));
        assertThat(alertJob.run(date(2026, 5, 5))).isEqualTo(1);
        assertThat(alertJob.run(date(2026, 5, 5))).isEqualTo(1);
        assertThat(alertsOf(run, date(2026, 5, 5))).isEqualTo(1);

        alertJob.run(date(2026, 5, 6));
        alertJob.run(date(2026, 5, 9));
        alertJob.run(date(2026, 5, 10));
        alertJob.run(date(2026, 5, 11));

        assertThat(alertsOf(run, date(2026, 5, 6))).isEqualTo(1);
        assertThat(alertsOf(run, date(2026, 5, 9))).isZero();
        assertThat(alertsOf(run, date(2026, 5, 10))).isZero();
        assertThat(alertsOf(run, date(2026, 5, 11))).isEqualTo(1);
        assertThat(alertsOf(run)).isEqualTo(3);
        assertThat(countOf("DRAFT", run)).isEqualTo(2);
    }

    @Test
    void approvingOnlyPartOfTheDraftsKeepsAlertingWithTheRemainingCountUntilTheLastOneIsApproved() {
        Career career = careerWithEnrollments(3);
        configureIssuanceMode("REVIEW", date(2026, 1, 1), career);
        MonthlyInvoiceRun run = runTheFirstOf(date(2026, 5, 1));
        List<Long> ids = invoiceIdsOf(run);
        today(date(2026, 5, 5));
        alertJob.run(date(2026, 5, 5));

        reviewer.approveDraftsOfRun(run.getId(), ids.subList(0, 2), UUID.randomUUID().toString(), REVIEWER);
        today(date(2026, 5, 6));
        alertJob.run(date(2026, 5, 6));

        assertThat(events.stream(DraftInvoicesAwaitingReviewEvent.class).filter(event -> event.alertDate().equals(date(2026, 5, 5))).findFirst().orElseThrow().pendingDrafts())
                .isEqualTo(3);
        assertThat(events.stream(DraftInvoicesAwaitingReviewEvent.class).filter(event -> event.alertDate().equals(date(2026, 5, 6))).findFirst().orElseThrow().pendingDrafts())
                .isEqualTo(1);
        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);

        today(date(2026, 5, 7));
        reviewer.approveDraftsOfRun(run.getId(), ids.subList(2, 3), UUID.randomUUID().toString(), REVIEWER);
        alertJob.run(date(2026, 5, 7));
        alertJob.run(date(2026, 5, 8));

        assertThat(statusOf(run)).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(alertsOf(run, date(2026, 5, 7))).isZero();
        assertThat(alertsOf(run, date(2026, 5, 8))).isZero();
        assertThat(alertsOf(run)).isEqualTo(2);
    }

    @Test
    void draftsLeftFromMayKeepAlertingInJuneWhileJuneRunsInTheNewlyConfiguredMode() {
        Career career = careerWithEnrollments(1);
        configureIssuanceMode("REVIEW", date(2026, 1, 1), career);
        configureIssuanceMode("DIRECT", date(2026, 6, 1), career);
        MonthlyInvoiceRun may = runTheFirstOf(date(2026, 5, 1));

        MonthlyInvoiceRun june = runTheFirstOf(date(2026, 6, 1));
        alertJob.run(date(2026, 6, 1));

        assertThat(may.getIssuanceMode()).isEqualTo(MonthlyInvoiceIssuanceMode.REVIEW);
        assertThat(june.getIssuanceMode()).isEqualTo(MonthlyInvoiceIssuanceMode.DIRECT);
        assertThat(statusOf(june)).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(alertsOf(may, date(2026, 6, 1))).isEqualTo(1);
        assertThat(alertsOf(june)).isZero();
    }
}
