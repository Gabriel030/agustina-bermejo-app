package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para cargar períodos sin fechas antes de volverlas obligatorias")
@Tag("migration")
class TermDatesNotNullMigrationTest {

    private static void insertTerm(JdbcTemplate jdbc, int year, String startDate, String endDate) {
        jdbc.update("insert into term (year_number, description, type, status, enrollment_start, enrollment_end, start_date, end_date) values (?, 'T', 'ANNUAL', 'ACTIVE', "
                + "timestamp '" + year + "-02-01 00:00:00', timestamp '" + year + "-02-20 00:00:00', "
                + (startDate == null ? "null" : "timestamp '" + startDate + "'") + ", "
                + (endDate == null ? "null" : "timestamp '" + endDate + "'") + ")", year);
    }

    @Test
    void termsWithoutDatesReceiveThemAndDatesBecomeMandatory() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("174")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertTerm(jdbc, 2027, null, null);
            insertTerm(jdbc, 2028, "2028-03-01 00:00:00", null);
            insertTerm(jdbc, 2029, null, "2029-12-15 00:00:00");
            insertTerm(jdbc, 2030, "2030-03-01 00:00:00", "2030-12-15 00:00:00");

            Flyway.configure().dataSource(database).locations("classpath:db/migration").target("176").load().migrate();

            assertThat(dates(jdbc, 2027)).isEqualTo("2027-02-01 00:00:00|2027-02-20 00:00:00");
            assertThat(dates(jdbc, 2028)).isEqualTo("2028-03-01 00:00:00|2028-03-01 00:00:00");
            assertThat(dates(jdbc, 2029)).isEqualTo("2029-02-01 00:00:00|2029-12-15 00:00:00");
            assertThat(dates(jdbc, 2030)).isEqualTo("2030-03-01 00:00:00|2030-12-15 00:00:00");
            assertThatThrownBy(() -> insertTerm(jdbc, 2031, null, "2031-12-15 00:00:00")).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> insertTerm(jdbc, 2032, "2032-03-01 00:00:00", null)).isInstanceOf(Exception.class);
        }
    }

    private static String dates(JdbcTemplate jdbc, int year) {
        return jdbc.queryForObject("select to_char(start_date, 'YYYY-MM-DD HH24:MI:SS') || '|' || to_char(end_date, 'YYYY-MM-DD HH24:MI:SS') from term where year_number = ?", String.class, year);
    }
}
