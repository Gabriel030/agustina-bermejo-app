package com.red_escuela.backend.billing.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentStandingChangesMetricIT extends AbstractMonthlyMetricsIT {

    private static final String UP_TO_DATE = "UP_TO_DATE";
    private static final String OVERDUE = "OVERDUE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String WITHDRAWN = "WITHDRAWN_FOR_NONPAYMENT";

    private final List<Long> standingIds = new ArrayList<>();
    private final List<Long> programEnrollmentIds = new ArrayList<>();
    private final List<Long> courseEnrollmentIds = new ArrayList<>();
    private final List<Long> programIds = new ArrayList<>();
    private final List<Long> courseIds = new ArrayList<>();
    private final List<Long> termIds = new ArrayList<>();

    @AfterEach
    void deleteStandings() {
        jdbc.execute("alter table enrollment_payment_status_change disable trigger trg_enrollment_payment_status_change_append_only");
        try {
            standingIds.forEach(id -> jdbc.update("delete from enrollment_payment_status_change where standing_id = ?", id));
        } finally {
            jdbc.execute("alter table enrollment_payment_status_change enable trigger trg_enrollment_payment_status_change_append_only");
        }
        standingIds.forEach(id -> jdbc.update("delete from enrollment_payment_standing where id = ?", id));
        programEnrollmentIds.forEach(id -> jdbc.update("delete from program_enrollment where id = ?", id));
        courseEnrollmentIds.forEach(id -> jdbc.update("delete from course_enrollment where id = ?", id));
        programIds.forEach(id -> jdbc.update("delete from program where id = ?", id));
        courseIds.forEach(id -> jdbc.update("delete from course where id = ?", id));
        termIds.forEach(id -> jdbc.update("delete from term where id = ?", id));
        standingIds.clear();
        programEnrollmentIds.clear();
        courseEnrollmentIds.clear();
        programIds.clear();
        courseIds.clear();
        termIds.clear();
    }

    private Long newProgramOf(Currency currency) {
        Long programId = jdbc.queryForObject("insert into program (name, currency) values ('Carrera de cobranza', ?) returning id", Long.class, currency.name());
        programIds.add(programId);
        return programId;
    }

    private Long newCourseOf(Currency currency) {
        Long termId = jdbc.queryForObject("insert into term (year_number, status, start_date, end_date) values (2021, true, timestamp '2021-03-01 00:00:00', timestamp '2021-12-15 00:00:00') returning id", Long.class);
        termIds.add(termId);
        Long courseId = jdbc.queryForObject("insert into course (name, term_id, currency) values ('Curso de cobranza', ?, ?) returning id", Long.class, termId, currency.name());
        courseIds.add(courseId);
        return courseId;
    }

    private Long newStandingInProgram(Long programId) {
        Long enrollmentId = jdbc.queryForObject("insert into program_enrollment (student_id, program_id, status, enrollment_date) values (?, ?, 'ENROLLED', now()) returning id",
                Long.class, newStudentId(), programId);
        programEnrollmentIds.add(enrollmentId);
        Long standingId = jdbc.queryForObject("insert into enrollment_payment_standing (program_enrollment_id, student_id, status, last_closed_month) "
                + "select id, student_id, 'UP_TO_DATE', date '2022-01-01' from program_enrollment where id = ? returning id", Long.class, enrollmentId);
        standingIds.add(standingId);
        return standingId;
    }

    private Long newStandingInCourse(Long courseId) {
        Long enrollmentId = jdbc.queryForObject("insert into course_enrollment (student_id, course_id, status, enrollment_date) values (?, ?, 'ENROLLED', now()) returning id",
                Long.class, newStudentId(), courseId);
        courseEnrollmentIds.add(enrollmentId);
        Long standingId = jdbc.queryForObject("insert into enrollment_payment_standing (course_enrollment_id, student_id, status, last_closed_month) "
                + "select id, student_id, 'UP_TO_DATE', date '2022-01-01' from course_enrollment where id = ? returning id", Long.class, enrollmentId);
        standingIds.add(standingId);
        return standingId;
    }

    private void recordChange(Long standingId, String event, String from, String to, LocalDate businessDate) {
        jdbc.update("insert into enrollment_payment_status_change (standing_id, event, from_status, to_status, actor_process, business_date, occurred_at) "
                + "values (?, ?, ?, ?, 'TEST', ?, cast(? as timestamp))", standingId, event, from, to, businessDate, businessDate + " 03:00:00");
    }

    private void suspend(Long standingId, LocalDate day) {
        recordChange(standingId, "OVERDUE_DEBT_DETECTED", UP_TO_DATE, OVERDUE, day.minusDays(20));
        recordChange(standingId, "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT", OVERDUE, SUSPENDED, day);
    }

    private static long countOf(JsonNode metric, String key, int monthIndex) {
        return seriesOf(metric, key).get("values").get(monthIndex).asLong();
    }

    @Test
    @Tag("D-122")
    @Tag("D-356")
    void aprilWithTwoSuspensionsOneLiftingAndOneWithdrawalCountsTwoOneOne() throws Exception {
        Long program = newProgramOf(Currency.ARS);
        Long first = newStandingInProgram(program);
        Long second = newStandingInProgram(program);
        Long third = newStandingInProgram(program);
        Long fourth = newStandingInProgram(program);
        suspend(first, LocalDate.of(2022, 4, 1));
        suspend(second, LocalDate.of(2022, 4, 1));
        suspend(third, LocalDate.of(2022, 2, 1));
        recordChange(third, "ACCOUNT_SETTLED", SUSPENDED, UP_TO_DATE, LocalDate.of(2022, 4, 12));
        suspend(fourth, LocalDate.of(2022, 3, 1));
        recordChange(fourth, "TERM_CLOSED_WHILE_SUSPENDED", SUSPENDED, WITHDRAWN, LocalDate.of(2022, 4, 30));
        recordChange(newStandingInProgram(program), "OVERDUE_DEBT_DETECTED", UP_TO_DATE, OVERDUE, LocalDate.of(2022, 4, 15));
        Long paidBeforeSuspension = newStandingInProgram(program);
        recordChange(paidBeforeSuspension, "OVERDUE_DEBT_DETECTED", UP_TO_DATE, OVERDUE, LocalDate.of(2022, 4, 2));
        recordChange(paidBeforeSuspension, "OVERDUE_DEBT_CLEARED", OVERDUE, UP_TO_DATE, LocalDate.of(2022, 4, 9));

        JsonNode april = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=" + program);

        assertThat(countOf(april, "suspended", 0)).isEqualTo(2);
        assertThat(countOf(april, "suspensionLifted", 0)).isEqualTo(1);
        assertThat(countOf(april, "withdrawnForNonpayment", 0)).isEqualTo(1);
        assertThat(countOf(april, "becameOverdue", 0)).isEqualTo(2);
    }

    @Test
    @Tag("D-122")
    void eachMonthCountsOnlyTheChangesOfItsOwnBusinessDate() throws Exception {
        Long program = newProgramOf(Currency.ARS);
        Long enrollment = newStandingInProgram(program);
        suspend(enrollment, LocalDate.of(2022, 3, 31));
        Long other = newStandingInProgram(program);
        suspend(other, LocalDate.of(2022, 5, 1));

        JsonNode marchAndApril = metric(directorToken, "payment-standing-changes", "from=2022-03&to=2022-04&currency=ARS&programId=" + program);

        assertThat(countOf(marchAndApril, "suspended", 0)).isEqualTo(1);
        assertThat(countOf(marchAndApril, "suspended", 1)).isEqualTo(0);
        assertThat(marchAndApril.get("months")).hasSize(2);
    }

    @Test
    @Tag("D-122")
    void anEnrollmentThatFallsBehindTwiceInTheSameMonthCountsOnce() throws Exception {
        Long program = newProgramOf(Currency.ARS);
        Long enrollment = newStandingInProgram(program);
        recordChange(enrollment, "OVERDUE_DEBT_DETECTED", UP_TO_DATE, OVERDUE, LocalDate.of(2022, 6, 3));
        recordChange(enrollment, "OVERDUE_DEBT_CLEARED", OVERDUE, UP_TO_DATE, LocalDate.of(2022, 6, 10));
        recordChange(enrollment, "OVERDUE_DEBT_DETECTED", UP_TO_DATE, OVERDUE, LocalDate.of(2022, 6, 20));

        JsonNode june = metric(directorToken, "payment-standing-changes", "from=2022-06&to=2022-06&currency=ARS&programId=" + program);

        assertThat(countOf(june, "becameOverdue", 0)).isEqualTo(1);
    }

    @Test
    @Tag("D-122")
    void theMigratedAndOpeningRecordsAreNotCountedAsChanges() throws Exception {
        Long program = newProgramOf(Currency.ARS);
        Long enrollment = newStandingInProgram(program);
        Long suspendedAtMigration = newStandingInProgram(program);
        recordChange(enrollment, "STANDING_OPENED", null, UP_TO_DATE, LocalDate.of(2022, 7, 1));
        recordChange(suspendedAtMigration, "MIGRATED", null, SUSPENDED, LocalDate.of(2022, 7, 1));
        recordChange(suspendedAtMigration, "MIGRATED", null, OVERDUE, LocalDate.of(2022, 7, 1));

        JsonNode july = metric(directorToken, "payment-standing-changes", "from=2022-07&to=2022-07&currency=ARS&programId=" + program);

        for (String key : List.of("becameOverdue", "suspended", "suspensionLifted", "withdrawnForNonpayment")) {
            assertThat(countOf(july, key, 0)).as(key).isZero();
        }
    }

    @Test
    @Tag("D-122")
    void theProgramFilterKeepsOnlyThatProgramAndTheCourseFilterOnlyThatCourse() throws Exception {
        Long programA = newProgramOf(Currency.ARS);
        Long programB = newProgramOf(Currency.ARS);
        Long course = newCourseOf(Currency.ARS);
        suspend(newStandingInProgram(programA), LocalDate.of(2022, 4, 1));
        suspend(newStandingInProgram(programA), LocalDate.of(2022, 4, 1));
        suspend(newStandingInProgram(programB), LocalDate.of(2022, 4, 1));
        suspend(newStandingInCourse(course), LocalDate.of(2022, 4, 1));

        JsonNode ofA = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=" + programA);
        JsonNode ofB = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=" + programB);
        JsonNode ofCourse = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&courseId=" + course);

        assertThat(countOf(ofA, "suspended", 0)).isEqualTo(2);
        assertThat(countOf(ofB, "suspended", 0)).isEqualTo(1);
        assertThat(countOf(ofCourse, "suspended", 0)).isEqualTo(1);
    }

    @Test
    @Tag("D-122")
    void withoutProgramOrCourseFilterProgramsAndCoursesAreCountedTogether() throws Exception {
        Long program = newProgramOf(Currency.ARS);
        Long course = newCourseOf(Currency.ARS);
        suspend(newStandingInProgram(program), LocalDate.of(2022, 9, 1));
        suspend(newStandingInCourse(course), LocalDate.of(2022, 9, 1));

        JsonNode september = metric(directorToken, "payment-standing-changes", "from=2022-09&to=2022-09&currency=ARS");

        assertThat(countOf(september, "suspended", 0)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aProgramAndACourseAtTheSameTimeAreNotAvailable() throws Exception {
        JsonNode metric = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=1&courseId=1");

        assertThat(metric.get("available").asBoolean()).isFalse();
    }

    @Test
    @Tag("D-097")
    void eachCurrencyCountsOnlyTheEnrollmentsOfItsOwnProgramsAndCourses() throws Exception {
        Long arsProgram = newProgramOf(Currency.ARS);
        Long usdProgram = newProgramOf(Currency.USD);
        suspend(newStandingInProgram(arsProgram), LocalDate.of(2022, 4, 1));
        suspend(newStandingInProgram(usdProgram), LocalDate.of(2022, 4, 1));
        suspend(newStandingInProgram(usdProgram), LocalDate.of(2022, 4, 1));

        JsonNode ars = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=" + arsProgram);
        JsonNode usd = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=USD&programId=" + usdProgram);
        JsonNode arsAskedWithUsdProgram = metric(directorToken, "payment-standing-changes", "from=2022-04&to=2022-04&currency=ARS&programId=" + usdProgram);

        assertThat(countOf(ars, "suspended", 0)).isEqualTo(1);
        assertThat(countOf(usd, "suspended", 0)).isEqualTo(2);
        assertThat(countOf(arsAskedWithUsdProgram, "suspended", 0)).isZero();
    }

    @Test
    @Tag("D-355")
    void directorAndAdministrativeReadThePanelWhilePreceptorAndStudentAreForbidden() throws Exception {
        String path = "/billing/metrics/payment-standing-changes?currency=ARS&from=2022-04&to=2022-04";

        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ADMINISTRATIVO").token())).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("PRECEPTOR").token())).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + newStaff("ALUMNO").token())).andExpect(status().isForbidden());
    }

    @Test
    void theRangeIsLimitedToThirtySixMonthsAndTheCurrencyIsRequiredWithTwoCurrencies() throws Exception {
        assertThat(errorOf(directorToken, "/billing/metrics/payment-standing-changes?currency=ARS&from=2018-01&to=2022-01", 400).get("code").asText())
                .isEqualTo("INVALID_MONTH_RANGE");
        assertThat(errorOf(directorToken, "/billing/metrics/payment-standing-changes?from=2022-04&to=2022-04", 400).get("code").asText())
                .isEqualTo("CURRENCY_REQUIRED");
    }
}
