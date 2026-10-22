package com.red_escuela.backend.billing;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValuesCodec;
import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para comprobar el backfill del resumen diario y las restricciones de la tabla nueva")
@Tag("migration")
class CollectionReminderMigrationTest {

    private static final String ONE_MESSAGE = "'[{\"format\": \"DAILY_DIGEST\"}]'::jsonb";

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static Long insertStudent(JdbcTemplate jdbc, String label) {
        jdbc.update("insert into address default values");
        Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
        jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', ?, ?, ?, ?)", "3" + label, label + "@example.com", label, addressId);
        Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
        jdbc.update("insert into institution (name) values (?)", "Instituto " + label);
        Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
        return jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id", Long.class, personId, institutionId);
    }

    @Test
    @Tag("D-411")
    void everyStoredCollectionReminderSettingsVersionGainsTheEnabledDailyDigestAndOtherScopesAreUntouched() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("103")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertStudent(jdbc, "actor.migracion");
            migrateTo(database, "133");
            String before = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            String otherScopesBefore = jdbc.queryForObject("select coalesce(string_agg(id || ':' || config_values::text, '|' order by id), '') from configuration_version "
                    + "where scope <> 'COLLECTION_REMINDERS'", String.class);
            assertThat(before).doesNotContain("dailyDigestEnabled");

            migrateTo(database, "134");

            String after = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(after).contains("\"dailyDigestEnabled\": true");
            assertThat(ConfigurationValuesCodec.fromJson(after)).isEqualTo(CollectionReminderSettings.initial());
            assertThat(jdbc.queryForObject("select coalesce(string_agg(id || ':' || config_values::text, '|' order by id), '') from configuration_version "
                    + "where scope <> 'COLLECTION_REMINDERS'", String.class)).isEqualTo(otherScopesBefore);
        }
    }

    @Test
    @Tag("D-353")
    void collectionReminderTableAcceptsOneRowPerStudentAndDayAndRejectsEmptyOrMalformedOnes() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("134")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            Long student = insertStudent(jdbc, "alumno.uno");
            String insert = "insert into collection_reminder (student_id, business_date, in_app_messages, email_messages, suppressed, created_at) values (?, '2027-03-01', %s, %s, %s, now())";

            jdbc.update(String.format(insert, ONE_MESSAGE, "'[]'::jsonb", "'[]'::jsonb"), student);

            assertThatThrownBy(() -> jdbc.update(String.format(insert, ONE_MESSAGE, ONE_MESSAGE, "'[]'::jsonb"), student)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_collection_reminder_student_day");
            jdbc.update(String.format(insert.replace("2027-03-01", "2027-03-02"), "'[]'::jsonb", "'[]'::jsonb", "'[{\"invoiceId\": 1}]'::jsonb"), student);
            assertThatThrownBy(() -> jdbc.update(String.format(insert.replace("2027-03-01", "2027-03-03"), "'[]'::jsonb", "'[]'::jsonb", "'[]'::jsonb"), student))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_collection_reminder_not_empty");
            assertThatThrownBy(() -> jdbc.update(String.format(insert.replace("2027-03-01", "2027-03-04"), "'{}'::jsonb", "'[]'::jsonb", "'[]'::jsonb"), student))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_collection_reminder_lists_are_arrays");
            assertThatThrownBy(() -> jdbc.update(String.format(insert.replace("2027-03-01", "2027-03-05"), ONE_MESSAGE, "'[]'::jsonb", "'[]'::jsonb"), student + 999))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
