package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para cargar períodos sin ventana de inscripción antes de volverla obligatoria")
@Tag("migration")
class TermEnrollmentWindowNotNullMigrationTest {

    private static void insertTerm(JdbcTemplate jdbc, int year, String enrollmentStart, String enrollmentEnd) {
        jdbc.update("insert into term (year_number, description, type, start_date, end_date, status, enrollment_start, enrollment_end) values (?, 'T', 'ANNUAL', "
                + "timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00', 'ACTIVE', "
                + (enrollmentStart == null ? "null" : "timestamp '" + enrollmentStart + "'") + ", "
                + (enrollmentEnd == null ? "null" : "timestamp '" + enrollmentEnd + "'") + ")", year);
    }

    @Test
    void termsWithoutWindowReceiveTheirValidityAndWindowBecomesMandatory() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("172")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertTerm(jdbc, 2027, null, null);
            insertTerm(jdbc, 2028, "2028-02-01 00:00:00", null);
            insertTerm(jdbc, 2029, "2029-02-01 00:00:00", "2029-02-20 00:00:00");

            Flyway.configure().dataSource(database).locations("classpath:db/migration").target("174").load().migrate();

            assertThat(window(jdbc, 2027)).isEqualTo("2027-03-01 00:00:00|2027-12-15 00:00:00");
            assertThat(window(jdbc, 2028)).isEqualTo("2028-02-01 00:00:00|2028-02-01 00:00:00");
            assertThat(window(jdbc, 2029)).isEqualTo("2029-02-01 00:00:00|2029-02-20 00:00:00");
            assertThatThrownBy(() -> insertTerm(jdbc, 2030, null, "2030-02-20 00:00:00")).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> insertTerm(jdbc, 2031, "2031-03-01 00:00:00", "2031-02-01 00:00:00")).isInstanceOf(Exception.class);
        }
    }

    private static String window(JdbcTemplate jdbc, int year) {
        return jdbc.queryForObject("select to_char(enrollment_start, 'YYYY-MM-DD HH24:MI:SS') || '|' || to_char(enrollment_end, 'YYYY-MM-DD HH24:MI:SS') from term where year_number = ?", String.class, year);
    }
}
