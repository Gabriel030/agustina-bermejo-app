package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValuesCodec;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Cada caso levanta su propio Postgres y aplica las 111 migraciones por etapas para cargar datos con la forma vieja; no se puede compartir el contenedor sin mezclar estados")
@Tag("migration")
@Tag("migration")
class CollectionReminderScheduleMigrationTest {

    private static JdbcTemplate migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private static void insertPerson(JdbcTemplate jdbc) {
        jdbc.update("insert into address default values");
        Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
        jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)",
                addressId);
    }

    @Test
    @Tag("D-368")
    @Tag("D-370")
    void groupsAreSeededFromTheEffectiveCadenceTheGlobalScopeLosesItAndTheChecksAcceptGroupsAndExceptions() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("103")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertPerson(jdbc);
            migrateTo(database, "110");
            jdbc.execute("alter table configuration_version disable trigger user");
            jdbc.update("update configuration_version set config_values = jsonb_set(jsonb_set(config_values, '{daysBeforeDueReminder}', '2'), "
                    + "'{emissionReminderEnabled}', 'false') where scope = 'COLLECTION_REMINDERS'");
            jdbc.execute("alter table configuration_version enable trigger user");

            migrateTo(database, "111");

            for (String scope : List.of("PROGRAM_REMINDER_SCHEDULE", "COURSE_REMINDER_SCHEDULE")) {
                String json = jdbc.queryForObject("select config_values::text from configuration_version where scope = ? and topic_key = ? and status = 'EFFECTIVE'",
                        String.class, scope, scope);
                ConfigurationValues values = ConfigurationValuesCodec.fromJson(json);
                ReminderSchedule schedule = values instanceof ProgramReminderSchedule program ? program.schedule()
                        : ((com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule) values).schedule();
                assertThat(schedule.followsGroupSchedule()).isFalse();
                assertThat(schedule.notices()).extracting(ReminderNotice::noticeKey).containsExactly("after-issue", "before-due", "after-due", "before-suspension");
                assertThat(schedule.notices()).extracting(ReminderNotice::offsetDays).containsExactly(5, -2, 10, -1);
                assertThat(schedule.notices().get(1).milestone()).isEqualTo(ReminderMilestone.DUE_DATE);
            }
            assertThat(jdbc.queryForObject("select count(*) from configuration_version_status_change c join configuration_version v on v.id = c.configuration_version_id "
                    + "where v.scope in ('PROGRAM_REMINDER_SCHEDULE', 'COURSE_REMINDER_SCHEDULE') and c.to_status = 'EFFECTIVE'", Long.class)).isEqualTo(2L);
            String global = jdbc.queryForObject("select config_values::text from configuration_version where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(global).doesNotContain("emissionReminderEnabled", "daysAfterIssueReminder", "daysBeforeDueReminder", "daysAfterDueReminder",
                    "daysBeforeSuspensionReminder");
            String globalWithoutCaps = jdbc.queryForObject("select (config_values - 'minDaysBetweenEmails' - 'maxReminderEmailsPerMonth')::text from configuration_version "
                    + "where scope = 'COLLECTION_REMINDERS'", String.class);
            assertThat(ConfigurationValuesCodec.fromJson(globalWithoutCaps)).isEqualTo(CollectionReminderSettings.initial());

            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            String insert = "insert into configuration_version (scope, topic_key, program_id, config_values, status, effective_from, change_reason, proposed_by_id, "
                    + "proposed_at, decided_by_id, decided_at) values (?, ?, ?, '{}'::jsonb, 'EFFECTIVE', current_date, 'x', ?, now(), ?, now())";
            jdbc.update(insert, "PROGRAM_REMINDER_SCHEDULE", "PROGRAM_REMINDER_SCHEDULE:P" + programId, programId, personId, personId);
            assertThatThrownBy(() -> jdbc.update(insert, "COURSE_REMINDER_SCHEDULE", "COURSE_REMINDER_SCHEDULE", programId, personId, personId))
                    .hasMessageContaining("ck_configuration_version_topic");
        }
    }

    @Test
    @Tag("D-368")
    void anOpenProposalOfTheGlobalScopeAbortsTheMigrationWithAClearMessage() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("103")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertPerson(jdbc);
            migrateTo(database, "110");
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at) "
                    + "select 'COLLECTION_REMINDERS', 'COLLECTION_REMINDERS', config_values, 'PROPOSED', current_date + 5, 'x', ?, now() "
                    + "from configuration_version where scope = 'COLLECTION_REMINDERS'", personId);

            assertThatThrownBy(() -> migrateTo(database, "111")).hasStackTraceContaining("Hay una propuesta o versión programada de Avisos de cobranza");
        }
    }
}
