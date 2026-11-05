package com.red_escuela.backend.billing.collections;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class EnrollmentPaymentStandingMigrationTest {

    @Test
    @Tag("D-185")
    void everyEnrollmentGetsOneStandingWithItsOldStatusAndAMigratedHistoryRowAndTheOldColumnsRemain() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("150")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into institution (name) values ('Instituto')");
            Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
            jdbc.update("insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
            Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
            jdbc.update("insert into term (year_number, status, start_date, end_date) values (2027, true, timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            jdbc.update("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS')", termId);
            Long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
            Long suspendedProgram = insertProgramEnrollment(jdbc, studentId, programId, "SUSPENDED", "timestamp '2027-04-12 10:00:00'");
            Long upToDateProgram = insertProgramEnrollment(jdbc, studentId, programId, "UP_TO_DATE", "null");
            Long overdueCourse = insertCourseEnrollment(jdbc, studentId, courseId, "OVERDUE", "timestamp '2027-05-03 09:00:00'");
            Long withdrawnCourse = insertCourseEnrollment(jdbc, studentId, courseId, "WITHDRAWN_FOR_NONPAYMENT", "timestamp '2027-06-03 09:00:00'");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("151").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from enrollment_payment_standing", Long.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from enrollment_payment_standing where last_closed_month = (date_trunc('month', current_date) - interval '1 month')::date",
                    Long.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where program_enrollment_id = ?", String.class, suspendedProgram)).isEqualTo("SUSPENDED");
            assertThat(jdbc.queryForObject("select suspended_since::text from enrollment_payment_standing where program_enrollment_id = ?", String.class, suspendedProgram))
                    .isEqualTo("2027-04-01");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where program_enrollment_id = ?", String.class, upToDateProgram)).isEqualTo("UP_TO_DATE");
            assertThat(jdbc.queryForObject("select suspended_since from enrollment_payment_standing where program_enrollment_id = ?", java.sql.Date.class, upToDateProgram)).isNull();
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, overdueCourse)).isEqualTo("OVERDUE");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, withdrawnCourse))
                    .isEqualTo("WITHDRAWN_FOR_NONPAYMENT");
            assertThat(jdbc.queryForObject("select student_id from enrollment_payment_standing where course_enrollment_id = ?", Long.class, overdueCourse)).isEqualTo(studentId);

            List<Map<String, Object>> history = jdbc.queryForList("select event, from_status, to_status, actor_process, business_date::text as business_date "
                    + "from enrollment_payment_status_change order by id");
            assertThat(history).hasSize(4);
            assertThat(history).allSatisfy(row -> {
                assertThat(row.get("event")).isEqualTo("MIGRATED");
                assertThat(row.get("from_status")).isNull();
                assertThat(row.get("actor_process")).isEqualTo("MIGRATION");
            });
            assertThat(jdbc.queryForObject("select c.business_date::text from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                    + "where s.program_enrollment_id = ?", String.class, suspendedProgram)).isEqualTo("2027-04-12");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'program_enrollment' and column_name = 'payment_status'", Long.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'course_enrollment' and column_name = 'payment_status'", Long.class))
                    .isEqualTo(1);
        }
    }

    @Test
    @Tag("D-185")
    void enrollmentsCreatedAfterTheFirstCopyGetTheirStandingAndHistoryAndTheOldColumnsAreDropped() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("151")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into institution (name) values ('Instituto')");
            Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
            jdbc.update("insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
            Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
            jdbc.update("insert into term (year_number, status, start_date, end_date) values (2027, true, timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            jdbc.update("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS')", termId);
            Long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
            Long suspendedProgram = insertProgramEnrollment(jdbc, studentId, programId, "SUSPENDED", "timestamp '2027-04-12 10:00:00'");
            Long upToDateProgram = insertProgramEnrollment(jdbc, studentId, programId, "UP_TO_DATE", "null");
            Long overdueCourse = insertCourseEnrollment(jdbc, studentId, courseId, "OVERDUE", "timestamp '2027-05-03 09:00:00'");
            Long withdrawnCourse = insertCourseEnrollment(jdbc, studentId, courseId, "WITHDRAWN_FOR_NONPAYMENT", "timestamp '2027-06-03 09:00:00'");
            Long alreadyTrackedCourse = insertCourseEnrollment(jdbc, studentId, courseId, "OVERDUE", "null");
            jdbc.update("insert into enrollment_payment_standing (course_enrollment_id, student_id, status, last_closed_month) values (?, ?, 'UP_TO_DATE', date '2027-01-01')",
                    alreadyTrackedCourse, studentId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("160").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from enrollment_payment_standing", Long.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where program_enrollment_id = ?", String.class, suspendedProgram)).isEqualTo("SUSPENDED");
            assertThat(jdbc.queryForObject("select suspended_since::text from enrollment_payment_standing where program_enrollment_id = ?", String.class, suspendedProgram))
                    .isEqualTo("2027-04-01");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where program_enrollment_id = ?", String.class, upToDateProgram)).isEqualTo("UP_TO_DATE");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, overdueCourse)).isEqualTo("OVERDUE");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, withdrawnCourse))
                    .isEqualTo("WITHDRAWN_FOR_NONPAYMENT");
            assertThat(jdbc.queryForObject("select status from enrollment_payment_standing where course_enrollment_id = ?", String.class, alreadyTrackedCourse)).isEqualTo("UP_TO_DATE");
            assertThat(jdbc.queryForObject("select last_closed_month::text from enrollment_payment_standing where course_enrollment_id = ?", String.class, alreadyTrackedCourse))
                    .isEqualTo("2027-01-01");
            assertThat(jdbc.queryForObject("select count(*) from enrollment_payment_status_change where event = 'MIGRATED' and actor_process = 'MIGRATION' and from_status is null", Long.class))
                    .isEqualTo(5);
            assertThat(jdbc.queryForObject("select count(*) from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                    + "where s.course_enrollment_id = ?", Long.class, alreadyTrackedCourse)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select c.to_status from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                    + "where s.course_enrollment_id = ?", String.class, withdrawnCourse)).isEqualTo("WITHDRAWN_FOR_NONPAYMENT");
            assertThat(jdbc.queryForObject("select c.business_date::text from enrollment_payment_status_change c join enrollment_payment_standing s on s.id = c.standing_id "
                    + "where s.program_enrollment_id = ?", String.class, suspendedProgram)).isEqualTo("2027-04-12");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name in ('program_enrollment', 'course_enrollment') "
                    + "and column_name in ('payment_status', 'payment_status_updated_at')", Long.class)).isZero();
        }
    }

    private Long insertProgramEnrollment(JdbcTemplate jdbc, Long studentId, Long programId, String paymentStatus, String updatedAt) {
        jdbc.update("insert into program_enrollment (student_id, program_id, status, payment_status, payment_status_updated_at) values (?, ?, 'ENROLLED', ?, " + updatedAt + ")",
                studentId, programId, paymentStatus);
        return jdbc.queryForObject("select max(id) from program_enrollment", Long.class);
    }

    private Long insertCourseEnrollment(JdbcTemplate jdbc, Long studentId, Long courseId, String paymentStatus, String updatedAt) {
        jdbc.update("insert into course_enrollment (student_id, course_id, status, payment_status, payment_status_updated_at) values (?, ?, 'ENROLLED', ?, " + updatedAt + ")",
                studentId, courseId, paymentStatus);
        return jdbc.queryForObject("select max(id) from course_enrollment", Long.class);
    }
}
