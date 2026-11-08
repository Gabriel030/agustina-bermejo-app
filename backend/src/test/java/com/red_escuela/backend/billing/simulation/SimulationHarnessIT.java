package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.run.operation.FailedInvoiceRetryJob;
import com.red_escuela.backend.billing.run.operation.MonthlyInvoiceIssuanceJob;
import com.red_escuela.backend.billing.run.operation.NightlyBillingJob;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SimulationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulationHarnessIT extends AbstractSimulationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    @Tag("D-160")
    @Tag("D-449")
    void timelineReadsCronFromRealJobBeans() {
        Timeline timeline = timeline(Timeline.Mode.DAILY);

        assertThat(timeline.jobs()).extracting(Timeline.Job::name)
                .containsExactlyInAnyOrder("NightlyBillingJob", "MonthlyInvoiceIssuanceJob", "FailedInvoiceRetryJob");
        ZonedDateTime from = LocalDateTime.of(2027, 3, 1, 0, 0).atZone(ZONE);
        for (Timeline.Job job : timeline.jobs()) {
            String expression = switch (job.name()) {
                case "NightlyBillingJob" -> NightlyBillingJob.CRON;
                case "MonthlyInvoiceIssuanceJob" -> MonthlyInvoiceIssuanceJob.CRON;
                case "FailedInvoiceRetryJob" -> FailedInvoiceRetryJob.CRON;
                default -> throw new IllegalStateException(job.name());
            };
            assertThat(job.cron().next(from)).isEqualTo(CronExpression.parse(expression).next(from));
            assertThat(job.zone()).isEqualTo(ZONE);
        }
    }

    @Test
    @Tag("D-153")
    @Tag("D-154")
    void invariantI10DetectsOverdueInvoiceUnderReview() {
        BillingInvariantChecker checker = BillingInvariantChecker.startingNow(jdbcTemplate);
        Scenario scenario = scenario(Timeline.Mode.SPARSE).enrollStudent(LocalDate.of(2027, 3, 1));
        assertThat(checker.anInvoiceUnderReviewNeitherIsOverdueNorAccruesLateFees()).isEmpty();
        Long invoiceId = jdbcTemplate.queryForObject("""
                select i.id from invoice i join student_current_account a on a.id = i.student_current_account_id
                where a.student_id = ? order by i.id limit 1
                """, Long.class, scenario.studentId());
        jdbcTemplate.update("update invoice set payment_status = 'OVERDUE' where id = ?", invoiceId);
        jdbcTemplate.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'receipts/sim.pdf', "
                + "(select due_date - 1 from invoice where id = ?), 'PENDING_REVIEW', 0)", invoiceId, invoiceId);

        List<String> violations = checker.anInvoiceUnderReviewNeitherIsOverdueNorAccruesLateFees();

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).startsWith("I-10").contains("invoice " + invoiceId);
        jdbcTemplate.update("update bank_transfer_receipt set uploaded_at = uploaded_at + interval '3 day' where invoice_id = ?", invoiceId);
        assertThat(checker.anInvoiceUnderReviewNeitherIsOverdueNorAccruesLateFees()).isEmpty();
    }

    @Test
    @Tag("D-178")
    void invariantI18DetectsPendingEventPublication() {
        BillingInvariantChecker checker = BillingInvariantChecker.startingNow(jdbcTemplate);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) values (?, 'sim.listener', 'sim.Event', '{}', now())", id);
        try {
            List<String> violations = checker.noEventPublicationIsPending();

            assertThat(violations).anyMatch(row -> row.startsWith("I-18") && row.contains(id.toString()));
        } finally {
            jdbcTemplate.update("delete from event_publication where id = ?", id);
        }
        assertThat(checker.noEventPublicationIsPending()).isEmpty();
    }

    @Test
    @Order(1)
    void firstScenarioLeavesTheClockInTheFuture() {
        scenario(Timeline.Mode.SPARSE).enrollStudent(LocalDate.of(2027, 3, 1)).advanceTo(LocalDate.of(2027, 3, 20));

        assertThat(MutableClock.INSTANCE.instant().atZone(ZONE).toLocalDate()).isEqualTo(LocalDate.of(2027, 3, 20));
    }

    @Test
    @Order(2)
    void clockIsResetBetweenScenarios() {
        assertThat(MutableClock.INSTANCE.instant().atZone(ZONE).toLocalDate()).isNotEqualTo(LocalDate.of(2027, 3, 20));

        Scenario scenario = scenario(Timeline.Mode.SPARSE).enrollStudent(LocalDate.of(2027, 3, 1)).finish();

        assertThat(scenario.ledger().rows()).isNotEmpty().allSatisfy(row -> assertThat(row.issueDate().getYear()).isEqualTo(2027));
    }
}
