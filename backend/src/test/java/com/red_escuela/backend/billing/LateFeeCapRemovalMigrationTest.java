package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class LateFeeCapRemovalMigrationTest {

    @Test
    @Tag("D-239")
    @Tag("D-210")
    void versionsWithTheOldCapShapeLoseItAndGetChargingDisabledWithAHistoryRow() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("93")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                    + "effective_from, created_by_id, created_at) values (5, 0.0500, 30, 2, date '2020-01-01', ?, now())", personId);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("103").load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and jsonb_exists(config_values, 'lateFeeCapMonths')", Long.class)).isPositive();
            Long versions = jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT'", Long.class);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("107").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and (jsonb_exists(config_values, 'lateFeeCapMonths') or jsonb_exists(config_values, 'lateFeeCapPercentage'))", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and config_values->>'lateFeeChargingEnabled' = 'false'", Long.class)).isEqualTo(versions);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version_status_change c join configuration_version v on v.id = c.configuration_version_id "
                    + "where v.scope = 'LATE_PAYMENT' and c.reason like 'D-239%'", Long.class)).isEqualTo(versions);
            assertThat(jdbc.queryForObject("select (config_values->>'gracePeriodDays')::int from configuration_version where scope = 'LATE_PAYMENT'", Integer.class)).isEqualTo(5);
        }
    }
}
