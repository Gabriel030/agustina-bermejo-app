package com.red_escuela.backend.attendance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendancePercentageMigrationTest {

    @Test
    void maximumAbsencePercentageInPointsIsStoredAsAFractionAfterTheMigration() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("81").load().migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table subject_section disable trigger all");
            jdbc.execute("alter table course_section disable trigger all");
            jdbc.update("insert into subject (name, code) values ('Materia sembrada', 'MS-1')");
            Long subjectId = jdbc.queryForObject("select max(id) from subject", Long.class);
            jdbc.update("insert into term (year_number, status) values (2026, true)");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into subject_section (subject_id, term_id, name, max_absence_percentage) "
                    + "values (?, ?, 'Comision A', 25.0)", subjectId, termId);
            jdbc.update("insert into subject_section (subject_id, term_id, name, max_absence_percentage) "
                    + "values (?, ?, 'Comision B', 37.5)", subjectId, termId);
            jdbc.update("insert into subject_section (subject_id, term_id, name) values (?, ?, 'Comision C')", subjectId, termId);
            jdbc.update("insert into course_section (course_subject_id, term_id, name, max_absence_percentage) "
                    + "values (1, ?, 'Comision curso', 25.0)", termId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject(
                    "select max_absence_percentage from subject_section where name = 'Comision A'", BigDecimal.class))
                    .isEqualByComparingTo("0.25");
            assertThat(jdbc.queryForObject(
                    "select max_absence_percentage from subject_section where name = 'Comision B'", BigDecimal.class))
                    .isEqualByComparingTo("0.375");
            assertThat(jdbc.queryForObject(
                    "select max_absence_percentage from subject_section where name = 'Comision C'", BigDecimal.class))
                    .isNull();
            assertThat(jdbc.queryForObject(
                    "select max_absence_percentage from course_section where name = 'Comision curso'", BigDecimal.class))
                    .isEqualByComparingTo("0.25");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into subject_section (subject_id, term_id, name, max_absence_percentage) values (?, ?, 'Invalida', 1.5)",
                    subjectId, termId)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
