package com.red_escuela.backend.attendance;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class AttendancePercentageMigrationTest {

    @Test
    void maximumAbsencePercentageInPointsIsStoredAsAFractionAfterTheMigration() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("81")) {

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
