package com.red_escuela.backend.billing;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValuesCodec;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para cargar filas con la forma vieja antes de la migración que quita los topes de mails")
class CollectionReminderEmailCapsRemovalMigrationTest {

    private static JdbcTemplate migrateTo(PostgreSQLContainer<?> postgres, String target) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private static String snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select string_agg(id || ':' || config_values::text, '|' order by id) from configuration_version", String.class);
    }

    private static String migrationScript() {
        try (InputStream stream = CollectionReminderEmailCapsRemovalMigrationTest.class.getResourceAsStream("/db/migration/V126__collection_reminder_remove_email_caps.sql")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @Tag("D-391")
    void storedValuesLoseTheCapsInEveryScopeAreIdempotentAndOtherScopesAreUntouched() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbc = migrateTo(postgres, "103");
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)",
                    addressId);
            migrateTo(postgres, "123");
            String capsOverrides = "'{\"minDaysBetweenEmails\": 2, \"maxReminderEmailsPerMonth\": 9, \"dailyEmailCap\": 200}'::jsonb";
            jdbc.update("update configuration_version set config_values = jsonb_set(config_values, '{schedule,sendingOverrides}', " + capsOverrides
                    + ") where scope = 'PROGRAM_REMINDER_SCHEDULE'");
            jdbc.update("update configuration_version set config_values = jsonb_set(config_values, '{schedule,sendingOverrides}', " + capsOverrides
                    + ") where scope = 'COURSE_REMINDER_SCHEDULE'");
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into configuration_version (scope, topic_key, program_id, config_values, status, effective_from, change_reason, proposed_by_id, "
                    + "proposed_at, decided_by_id, decided_at) values ('PROGRAM_REMINDER_SCHEDULE', ?, ?, "
                    + "'{\"type\": \"PROGRAM_REMINDER_SCHEDULE\", \"schedule\": {\"followsGroupSchedule\": true, \"notices\": []}}'::jsonb, "
                    + "'EFFECTIVE', current_date, 'x', ?, now(), ?, now())", "PROGRAM_REMINDER_SCHEDULE:P" + programId, programId, personId, personId);
            jdbc.update("insert into configuration_version (scope, topic_key, program_id, config_values, status, effective_from, change_reason, proposed_by_id, "
                    + "proposed_at, decided_by_id, decided_at) values ('PROGRAM_REMINDER_SCHEDULE', ?, ?, "
                    + "'{\"type\": \"PROGRAM_REMINDER_SCHEDULE\", \"schedule\": {\"followsGroupSchedule\": false, \"notices\": [], \"sendingOverrides\": null}}'::jsonb, "
                    + "'SUPERSEDED', current_date, 'x', ?, now(), ?, now())", "PROGRAM_REMINDER_SCHEDULE:P" + programId + ":null", programId, personId, personId);
            jdbc.update("insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, "
                    + "decided_by_id, decided_at) values ('LATE_PAYMENT', 'LATE_PAYMENT', "
                    + "'{\"minDaysBetweenEmails\": 7, \"maxReminderEmailsPerMonth\": 8}'::jsonb, 'EFFECTIVE', current_date, 'x', ?, now(), ?, now())", personId, personId);
            String before = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(before).contains("minDaysBetweenEmails", "maxReminderEmailsPerMonth");

            migrateTo(postgres, "126");
            String afterFirst = snapshot(jdbc);
            jdbc.execute(migrationScript());

            assertThat(snapshot(jdbc)).isEqualTo(afterFirst);
            String global = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(global).doesNotContain("minDaysBetweenEmails", "maxReminderEmailsPerMonth");
            assertThat(ConfigurationValuesCodec.fromJson(global)).isEqualTo(CollectionReminderSettings.initial());
            for (String scope : new String[]{"PROGRAM_REMINDER_SCHEDULE", "COURSE_REMINDER_SCHEDULE"}) {
                String json = jdbc.queryForObject("select config_values::text from configuration_version where scope = ? and topic_key = ?", String.class, scope, scope);
                assertThat(json).doesNotContain("minDaysBetweenEmails", "maxReminderEmailsPerMonth").contains("\"dailyEmailCap\": 200");
                assertThat(ConfigurationValuesCodec.fromJson(json)).isNotNull();
            }
            String absent = jdbc.queryForObject("select config_values::text from configuration_version where topic_key = ?", String.class, "PROGRAM_REMINDER_SCHEDULE:P" + programId);
            assertThat(absent).doesNotContain("sendingOverrides");
            assertThat(ConfigurationValuesCodec.fromJson(absent)).isNotNull();
            String jsonNull = jdbc.queryForObject("select config_values::text from configuration_version where topic_key = ?", String.class,
                    "PROGRAM_REMINDER_SCHEDULE:P" + programId + ":null");
            assertThat(jsonNull).contains("\"sendingOverrides\": null");
            assertThat(jdbc.queryForObject("select config_values::text from configuration_version where scope = 'LATE_PAYMENT'", String.class))
                    .contains("\"minDaysBetweenEmails\": 7", "\"maxReminderEmailsPerMonth\": 8");
        }
    }
}
