package com.red_escuela.backend.enrollment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-249")
class EnrollmentWithdrawalAuditMigrationTest {

    @Test
    void withdrawnEnrollmentsKeepTheirCompletionDateAsEffectiveDateAndNothingElseGetsOne() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("114").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into institution (name) values ('Instituto Seed')");
            Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
            jdbc.update("insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
            Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            jdbc.update("insert into term (year_number, status, start_date, end_date) values (2027, true, timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS')", termId);
            Long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
            jdbc.update("insert into program_enrollment (student_id, program_id, status, enrollment_date, completion_date) values (?, ?, 'WITHDRAWN', "
                    + "timestamp '2027-03-01 10:00:00', timestamp '2027-05-15 23:30:00')", studentId, programId);
            Long withdrawnProgramEnrollment = jdbc.queryForObject("select max(id) from program_enrollment", Long.class);
            jdbc.update("insert into program_enrollment (student_id, program_id, status, enrollment_date) values (?, ?, 'ENROLLED', timestamp '2027-03-01 10:00:00')", studentId, programId);
            Long activeProgramEnrollment = jdbc.queryForObject("select max(id) from program_enrollment", Long.class);
            jdbc.update("insert into course_enrollment (student_id, course_id, status, enrollment_date, completion_date) values (?, ?, 'WITHDRAWN', "
                    + "timestamp '2027-03-01 10:00:00', timestamp '2027-04-02 08:00:00')", studentId, courseId);
            Long withdrawnCourseEnrollment = jdbc.queryForObject("select max(id) from course_enrollment", Long.class);
            jdbc.update("insert into course_enrollment (student_id, course_id, status, enrollment_date) values (?, ?, 'ENROLLED', timestamp '2027-03-01 10:00:00')", studentId, courseId);
            Long activeCourseEnrollment = jdbc.queryForObject("select max(id) from course_enrollment", Long.class);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("115").load().migrate();

            assertThat(jdbc.queryForObject("select withdrawal_effective_date::text from program_enrollment where id = ?", String.class, withdrawnProgramEnrollment)).isEqualTo("2027-05-15");
            assertThat(jdbc.queryForObject("select withdrawal_effective_date::text from course_enrollment where id = ?", String.class, withdrawnCourseEnrollment)).isEqualTo("2027-04-02");
            assertThat(jdbc.queryForObject("select withdrawal_effective_date from program_enrollment where id = ?", java.sql.Date.class, activeProgramEnrollment)).isNull();
            assertThat(jdbc.queryForObject("select withdrawal_effective_date from course_enrollment where id = ?", java.sql.Date.class, activeCourseEnrollment)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from program_enrollment where withdrawn_by_person_id is not null", Long.class)).isZero();
            assertThatThrownBy(() -> jdbc.update("update program_enrollment set withdrawal_effective_date = date '2027-05-01' where id = ?", activeProgramEnrollment))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update course_enrollment set withdrawal_effective_date = date '2027-05-01' where id = ?", activeCourseEnrollment))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update program_enrollment set withdrawn_by_person_id = 999999 where id = ?", withdrawnProgramEnrollment))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
