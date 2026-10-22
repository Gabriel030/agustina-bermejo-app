package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class ConfigurationCutoverMigrationTest {

    @Test
    @Tag("D-095")
    @Tag("D-210")
    void rowsWrittenAfterTheVersionedTablesWereCreatedAreBackfilledWithoutTouchingTheEarlierOnes() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("93")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into term (year_number, status, start_date, end_date) values (2020, true, timestamp '2020-03-01 00:00:00', timestamp '2020-12-15 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                    + "effective_from, created_by_id, created_at) values (5, 0.0500, 30, 2, date '2019-01-01', ?, timestamp '2019-01-01 10:00:00')", personId);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("98").load().migrate();
            long versionsBefore = jdbc.queryForObject("select count(*) from configuration_version", Long.class);
            Long earlierLateVersionId = jdbc.queryForObject("select id from configuration_version where scope = 'LATE_PAYMENT'", Long.class);
            jdbc.update("insert into program_year_billing_template (program_id, term_id, recurring_fee_invoices_count, enrollment_fee_count, first_enrollment_fee_month, "
                    + "second_enrollment_fee_month, enrollment_fee_lead_months, late_enrollment_fee_due_days, recurring_fee_due_days, withdraw_suspended_at_term_end, "
                    + "pays_while_paused, blocks_enrollment, blocks_exam_registration, blocks_certificates, require_enrollment_fee_for_final_exam_only_students, "
                    + "created_by_id, created_at) values (?, ?, 10, 2, 2, 7, 1, 12, 15, true, true, true, false, true, true, ?, timestamp '2020-04-01 10:00:00')",
                    programId, termId, personId);
            jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                    + "effective_from, created_by_id, created_at) values (7, 0.0800, 40, 3, date '2020-06-01', ?, timestamp '2020-06-01 10:00:00')", personId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("99").load().migrate();

            assertThat(jdbc.queryForObject("select effective_from::text from configuration_version where scope = 'YEAR_TERMS'", String.class)).isEqualTo("2020-03-01");
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT'", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and status = 'EFFECTIVE'", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select (config_values->>'gracePeriodDays')::int from configuration_version where scope = 'LATE_PAYMENT' and status = 'EFFECTIVE'", Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("select status from configuration_version where id = ?", String.class, earlierLateVersionId)).isEqualTo("SUPERSEDED");
            assertThat(jdbc.queryForObject("select count(*) from configuration_version_status_change where configuration_version_id = ?", Long.class, earlierLateVersionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version v where not exists (select 1 from configuration_version_status_change c where c.configuration_version_id = v.id)", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from configuration_version", Long.class)).isGreaterThan(versionsBefore);
        }
    }
}
