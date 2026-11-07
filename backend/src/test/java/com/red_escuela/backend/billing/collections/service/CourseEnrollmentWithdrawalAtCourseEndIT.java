package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.operation.TermEndWithdrawalBillingRunStep;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-123")
@Tag("D-142")
@Tag("D-246")
@Tag("D-293")
@SlowTest(reason = "Shares the heavy Spring context of the late fee scenario hierarchy: when it is the first class of its shard it absorbs the whole context start-up")
class CourseEnrollmentWithdrawalAtCourseEndIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private TermEndWithdrawalBillingRunStep termEndStep;

    private void arrangeCourseEndingOn(int year, LocalDate courseEnd, String standingStatus) {
        jdbc.update("update course_enrollment set status = 'WITHDRAWN' where id in (select course_enrollment_id from enrollment_payment_standing where status = 'SUSPENDED')");
        arrange(year);
        publishDefaults(year);
        jdbc.update("update term set end_date = ?::timestamp where id = ?", courseEnd + " 00:00:00", term.getId());
        String suspendedSince = "SUSPENDED".equals(standingStatus) ? "date '" + year + "-04-01'" : "null";
        jdbc.update("insert into enrollment_payment_standing (course_enrollment_id, student_id, status, suspended_since, last_closed_month) "
                + "values (?, ?, ?, " + suspendedSince + ", date '" + year + "-04-01')", enrollment.getId(), student.getId(), standingStatus);
    }

    private BillingRunStepOutcome runTermEndStepOn(LocalDate businessDate) {
        MutableClock.INSTANCE.setDate(businessDate);
        BillingRunStepOutcome outcome = termEndStep.runStepForBusinessDate(businessDate);
        awaitEventsDrained();
        return outcome;
    }

    private String enrollmentStatus() {
        return jdbc.queryForObject("select status from course_enrollment where id = ?", String.class, enrollment.getId());
    }

    private String standingStatus() {
        return jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, enrollment.getId());
    }

    private long standingChanges() {
        return jdbc.queryForObject("select count(*) from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                + "where s.course_enrollment_id = ?", Long.class, enrollment.getId());
    }

    @Test
    void theLastDayOfTheCourseDoesNotWithdrawTheSuspendedEnrollment() {
        arrangeCourseEndingOn(2120, d(2120, 6, 30), "SUSPENDED");

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2120, 6, 30));

        assertThat(outcome.candidates()).isZero();
        assertThat(enrollmentStatus()).isEqualTo("ENROLLED");
        assertThat(standingStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void theDayAfterTheCourseEndsWithdrawsTheSuspendedEnrollmentAndKeepsTheDebtAlive() {
        arrangeCourseEndingOn(2121, d(2121, 6, 30), "SUSPENDED");
        Invoice unpaidJune = overdueInstallmentDueOn(d(2121, 6, 10));
        long creditNotesBefore = jdbc.queryForObject("select count(*) from credit_note", Long.class);
        BigDecimal outstandingBefore = jdbc.queryForObject("select outstanding_amount from invoice where id = ?", BigDecimal.class, unpaidJune.getId());

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2121, 7, 1));

        assertThat(outcome.candidates()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(1);
        assertThat(outcome.failed()).isZero();
        assertThat(enrollmentStatus()).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select withdrawal_effective_date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2121-07-01");
        assertThat(jdbc.queryForObject("select withdrawn_by_person_id from course_enrollment where id = ?", Long.class, enrollment.getId())).isNull();
        assertThat(standingStatus()).isEqualTo("WITHDRAWN_FOR_NONPAYMENT");
        assertThat(jdbc.queryForList("select event, from_status, to_status, business_date::text as business_date from enrollment_payment_status_change c "
                + "join enrollment_payment_standing s on s.id = c.standing_id where s.course_enrollment_id = ? order by c.id desc limit 1", enrollment.getId()))
                .containsExactly(Map.of("event", "TERM_CLOSED_WHILE_SUSPENDED", "from_status", "SUSPENDED", "to_status", "WITHDRAWN_FOR_NONPAYMENT", "business_date", "2121-07-01"));
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, unpaidJune.getId())).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select outstanding_amount from invoice where id = ?", BigDecimal.class, unpaidJune.getId())).isEqualByComparingTo(outstandingBefore);
        assertThat(jdbc.queryForObject("select count(*) from credit_note", Long.class)).isEqualTo(creditNotesBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void retryingTheStepAfterTheWithdrawalDoesNothingAndDoesNotDuplicateTheHistory() {
        arrangeCourseEndingOn(2122, d(2122, 6, 30), "SUSPENDED");
        runTermEndStepOn(d(2122, 7, 1));
        long changesAfterFirstRun = standingChanges();

        BillingRunStepOutcome retry = runTermEndStepOn(d(2122, 7, 2));

        assertThat(retry.candidates()).isZero();
        assertThat(retry.failed()).isZero();
        assertThat(enrollmentStatus()).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select withdrawal_effective_date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2122-07-01");
        assertThat(standingChanges()).isEqualTo(changesAfterFirstRun);
    }

    @Test
    void anEnrollmentWithdrawnByTheStudentBeforeTheCourseEndIsNotATermEndCandidate() {
        arrangeCourseEndingOn(2123, d(2123, 6, 30), "SUSPENDED");
        withdrawCourseEnrollmentOn(d(2123, 5, 10));

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2123, 7, 1));

        assertThat(outcome.candidates()).isZero();
        assertThat(outcome.failed()).isZero();
        assertThat(jdbc.queryForObject("select withdrawal_effective_date from course_enrollment where id = ?", java.sql.Date.class, enrollment.getId())).isNull();
    }

    @Test
    void anOverdueButNotSuspendedEnrollmentIsNotWithdrawnWhenTheCourseEnds() {
        arrangeCourseEndingOn(2124, d(2124, 6, 30), "OVERDUE");
        overdueInstallmentDueOn(d(2124, 6, 10));

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2124, 7, 1));

        assertThat(outcome.candidates()).isZero();
        assertThat(enrollmentStatus()).isEqualTo("ENROLLED");
        assertThat(standingStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void anEnrollmentThatPaidBeforeTheCourseEndsIsNotWithdrawn() {
        arrangeCourseEndingOn(2125, d(2125, 6, 30), "UP_TO_DATE");

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2125, 7, 1));

        assertThat(outcome.candidates()).isZero();
        assertThat(enrollmentStatus()).isEqualTo("ENROLLED");
        assertThat(standingStatus()).isEqualTo("UP_TO_DATE");
    }

    @Test
    void aSuspendedEnrollmentOfAnotherCourseStillRunningIsLeftAloneWhileTheEndedOneIsWithdrawn() {
        arrangeCourseEndingOn(2126, d(2126, 6, 30), "SUSPENDED");
        Long runningTermId = jdbc.queryForObject("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) "
                + "values (2126, 'ACTIVE', timestamp '2126-03-01 00:00:00', timestamp '2126-12-15 00:00:00', timestamp '2000-01-01 00:00:00', timestamp '2999-12-31 00:00:00') returning id", Long.class);
        Long runningCourseId = jdbc.queryForObject("insert into course (name, term_id, currency) values ('Curso en marcha', ?, 'ARS') returning id", Long.class, runningTermId);
        Long runningEnrollmentId = jdbc.queryForObject("insert into course_enrollment (student_id, course_id, status, enrollment_date) values (?, ?, 'ENROLLED', now()) returning id",
                Long.class, student.getId(), runningCourseId);
        jdbc.update("insert into enrollment_payment_standing (course_enrollment_id, student_id, status, suspended_since, last_closed_month) values (?, ?, 'SUSPENDED', date '2126-04-01', date '2126-04-01')",
                runningEnrollmentId, student.getId());

        BillingRunStepOutcome outcome = runTermEndStepOn(d(2126, 7, 1));

        assertThat(outcome.candidates()).isEqualTo(1);
        assertThat(enrollmentStatus()).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select status from course_enrollment where id = ?", String.class, runningEnrollmentId)).isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, runningEnrollmentId)).isEqualTo("SUSPENDED");
    }

    @Test
    void theCourseEndingWithAnUnpaidInstallmentFreezesTheLateFeeFromTheWithdrawalDay() {
        arrangeCourseEndingOn(2127, d(2127, 6, 30), "SUSPENDED");
        Invoice origin = overdueInstallmentDueOn(d(2127, 6, 10));
        reconcile(origin, d(2127, 6, 30));

        runTermEndStepOn(d(2127, 7, 1));
        reconcile(origin, d(2127, 7, 2));
        List<String> feesRightAfterWithdrawal = summaryOf(origin);
        reconcile(origin, d(2127, 8, 15));

        assertThat(jdbc.queryForObject("select completion_date::date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2127-07-01");
        assertThat(feesRightAfterWithdrawal).isNotEmpty();
        assertThat(summaryOf(origin)).isEqualTo(feesRightAfterWithdrawal);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, origin.getId())).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }
}
