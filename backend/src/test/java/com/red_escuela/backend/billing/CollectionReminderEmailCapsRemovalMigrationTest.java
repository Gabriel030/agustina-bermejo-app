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

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para cargar filas con la forma vieja antes de la migración que quita los topes de mails")
class CollectionReminderEmailCapsRemovalMigrationTest {

    private static JdbcTemplate migrateTo(PostgreSQLContainer<?> postgres, String target) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
        return new JdbcTemplate(dataSource);
    }

    @Test
    @Tag("D-391")
    void storedValuesLoseTheCapsAndStillDeserializeAfterTheMigration() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbc = migrateTo(postgres, "103");
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)",
                    addressId);
            migrateTo(postgres, "123");
            jdbc.update("update configuration_version set config_values = jsonb_set(config_values, '{schedule,sendingOverrides}', "
                    + "'{\"minDaysBetweenEmails\": 2, \"maxReminderEmailsPerMonth\": 9, \"dailyEmailCap\": 200}'::jsonb) where scope = 'PROGRAM_REMINDER_SCHEDULE'");
            String before = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(before).contains("minDaysBetweenEmails", "maxReminderEmailsPerMonth");

            migrateTo(postgres, "126");

            String global = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(global).doesNotContain("minDaysBetweenEmails", "maxReminderEmailsPerMonth");
            assertThat(ConfigurationValuesCodec.fromJson(global)).isEqualTo(CollectionReminderSettings.initial());
            String program = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'PROGRAM_REMINDER_SCHEDULE'", String.class);
            assertThat(program).doesNotContain("minDaysBetweenEmails", "maxReminderEmailsPerMonth").contains("\"dailyEmailCap\": 200");
            assertThat(ConfigurationValuesCodec.fromJson(program)).isNotNull();
        }
    }
}
