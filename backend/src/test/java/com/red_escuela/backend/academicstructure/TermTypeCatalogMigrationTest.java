package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class TermTypeCatalogMigrationTest {

    private static void insertTerm(JdbcTemplate jdbc, int year, String type, String status, String start, String end) {
        jdbc.update("insert into term (year_number, type, status, start_date, end_date) values (?, ?, ?, "
                + (start == null ? "null" : "timestamp '" + start + "'") + ", "
                + (end == null ? "null" : "timestamp '" + end + "'") + ")", year, type, status);
    }

    private static void insertIrregularTermsVersion(JdbcTemplate jdbc, String values) {
        jdbc.update("insert into address default values");
        jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', (select max(id) from address))");
        jdbc.update("insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at, direct_publication) "
                + "select 'IRREGULAR_TERMS', 'IRREGULAR_TERMS', ?::jsonb, 'EFFECTIVE', current_date, 'Seed', max(id), now(), max(id), now(), true from person", values);
    }

    private static void migrate(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void freeTextTypesAreNormalizedIntoTheCatalogAndUnknownOnesAreInferredFromTheDates() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("165")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertTerm(jdbc, 2030, "ANUAL", "ACTIVE", "2030-03-01 00:00:00", "2030-12-15 00:00:00");
            insertTerm(jdbc, 2031, " 1c ", "ACTIVE", "2031-03-01 00:00:00", "2031-07-15 00:00:00");
            insertTerm(jdbc, 2032, "2C", "ACTIVE", "2032-08-01 00:00:00", "2032-12-15 00:00:00");
            insertTerm(jdbc, 2033, null, "ACTIVE", null, null);
            insertTerm(jdbc, 2034, "Intensivo de verano", "ACTIVE", "2034-01-05 00:00:00", "2034-03-20 00:00:00");
            insertTerm(jdbc, 2035, "Especial", "ACTIVE", "2035-08-01 00:00:00", "2035-11-20 00:00:00");
            insertTerm(jdbc, 2036, "Otro", "ACTIVE", "2036-03-01 00:00:00", "2036-12-20 00:00:00");

            migrate(dataSource);

            List<String> types = jdbc.queryForList(
                    "select type from term where year_number between 2030 and 2036 order by year_number", String.class);
            assertThat(types).containsExactly("ANNUAL", "FIRST_SEMESTER", "SECOND_SEMESTER", "ANNUAL",
                    "INTENSIVE", "SECOND_SEMESTER", "ANNUAL");
        }
    }

    @Test
    void theIrregularTermsConfigurationIsRewrittenFromFreeTextToCatalogValues() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("165")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertIrregularTermsVersion(jdbc, "{\"type\":\"IRREGULAR_TERMS\",\"types\":[\" Intensivo de verano\",\"1C\",\"intensivo de verano\"]}");

            migrate(dataSource);

            assertThat(jdbc.queryForObject("select config_values::text from configuration_version where scope = 'IRREGULAR_TERMS'", String.class))
                    .isEqualTo("{\"type\": \"IRREGULAR_TERMS\", \"types\": [\"FIRST_SEMESTER\", \"INTENSIVE\"]}");
        }
    }

    @Test
    void theMigrationFailsWhenTheIrregularTermsConfigurationHoldsATypeOutsideTheCatalog() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("165")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertIrregularTermsVersion(jdbc, "{\"type\":\"IRREGULAR_TERMS\",\"types\":[\"Libre\"]}");

            assertThatThrownBy(() -> migrate(dataSource))
                    .hasMessageContaining("Libre")
                    .hasMessageContaining("catálogo");
        }
    }

    @Test
    void theMigrationFailsWithAClearMessageWhenTwoActiveTermsCollapseIntoTheSameYearAndType() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("165")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertTerm(jdbc, 2040, "1C", "ACTIVE", "2040-03-01 00:00:00", "2040-07-15 00:00:00");
            insertTerm(jdbc, 2040, "Primer cuatrimestre", "ACTIVE", "2040-03-01 00:00:00", "2040-07-15 00:00:00");

            assertThatThrownBy(() -> migrate(dataSource))
                    .hasMessageContaining("2040/FIRST_SEMESTER")
                    .hasMessageContaining("duplicados");
        }
    }

    @Test
    void inactiveDuplicatesAreKeptAndTheCatalogConstraintsApplyAfterMigrating() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("165")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertTerm(jdbc, 2050, "1C", "ACTIVE", "2050-03-01 00:00:00", "2050-07-15 00:00:00");
            insertTerm(jdbc, 2050, "1C", "INACTIVE", "2050-03-01 00:00:00", "2050-07-15 00:00:00");

            migrate(dataSource);

            assertThat(jdbc.queryForObject("select count(*) from term where year_number = 2050", Integer.class))
                    .isEqualTo(2);
            assertThatThrownBy(() -> insertTerm(jdbc, 2050, "FIRST_SEMESTER", "ACTIVE", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertTerm(jdbc, 2051, "1C", "ACTIVE", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("insert into term (year_number, status) values (2052, 'ACTIVE')");
            assertThat(jdbc.queryForObject("select type from term where year_number = 2052", String.class))
                    .isEqualTo("ANNUAL");
        }
    }
}
