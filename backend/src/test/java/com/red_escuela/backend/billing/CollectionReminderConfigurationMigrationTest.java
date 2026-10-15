package com.red_escuela.backend.billing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionReminderConfigurationMigrationTest {

    @Test
    @Tag("D-351")
    @Tag("D-368")
    void versionOneIsSeededWithTheDefaultsAndTheChecksAcceptTheGlobalScopeButRejectATopicWithProgram() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("103").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("106").load().migrate();

            Map<String, Object> version = jdbc.queryForMap("select status, topic_key, effective_from, direct_publication, program_id, "
                    + "(config_values->>'emissionReminderEnabled')::boolean as emission, (config_values->>'daysAfterIssueReminder')::int as after_issue, "
                    + "(config_values->>'daysBeforeDueReminder')::int as before_due, (config_values->>'daysAfterDueReminder')::int as after_due, "
                    + "(config_values->>'daysBeforeSuspensionReminder')::int as before_suspension, (config_values->>'dailyEmailCap')::int as cap, "
                    + "config_values->>'emailWindowStart' as window_start, config_values->>'emailWindowEnd' as window_end, "
                    + "(config_values->>'minDaysBetweenEmails')::int as spacing, (config_values->>'maxReminderEmailsPerMonth')::int as monthly_max, "
                    + "(config_values->>'postWithdrawalSummaryMonths')::int as months, (config_values->>'contactListDaysOverdue')::int as contact_days, "
                    + "(config_values->>'contactListOverdueInstallments')::int as contact_installments from configuration_version where scope = 'COLLECTION_REMINDERS'");
            assertThat(version.get("status")).isEqualTo("EFFECTIVE");
            assertThat(version.get("topic_key")).isEqualTo("COLLECTION_REMINDERS");
            assertThat(version.get("direct_publication")).isEqualTo(true);
            assertThat(version.get("program_id")).isNull();
            assertThat(version).containsEntry("emission", true).containsEntry("after_issue", 5).containsEntry("before_due", 1).containsEntry("after_due", 10)
                    .containsEntry("before_suspension", 1).containsEntry("cap", 250).containsEntry("window_start", "09:00:00").containsEntry("window_end", "11:00:00")
                    .containsEntry("spacing", 3).containsEntry("monthly_max", 4).containsEntry("months", 6).containsEntry("contact_days", 14)
                    .containsEntry("contact_installments", 2);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version_status_change c join configuration_version v on v.id = c.configuration_version_id "
                    + "where v.scope = 'COLLECTION_REMINDERS' and c.to_status = 'EFFECTIVE'", Long.class)).isEqualTo(1L);

            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            assertThatThrownBy(() -> jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("set session_replication_role = replica");
                    statement.execute("insert into configuration_version (scope, topic_key, program_id, config_values, status, effective_from, change_reason, "
                            + "proposed_by_id, proposed_at, decided_by_id, decided_at) values ('COLLECTION_REMINDERS', 'COLLECTION_REMINDERS', 987654, '{}'::jsonb, "
                            + "'EFFECTIVE', current_date, 'x', " + personId + ", now(), " + personId + ", now())");
                }
                return null;
            })).hasMessageContaining("ck_configuration_version_topic");
            assertThatThrownBy(() -> jdbc.update("insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, "
                    + "proposed_by_id, proposed_at, decided_by_id, decided_at) values ('NOT_A_SCOPE', 'X', '{}'::jsonb, 'EFFECTIVE', current_date, 'x', ?, now(), ?, now())",
                    personId, personId)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @Tag("D-351")
    void withoutAnyPersonNoVersionIsSeededAndTheMigrationStillApplies() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("103").load().migrate();
            jdbc.update("delete from person");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("106").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'COLLECTION_REMINDERS'", Long.class)).isZero();
        }
    }
}
