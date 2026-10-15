package com.red_escuela.backend.billing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyLateFeeMigrationTest {

    private static void migrateTo(DriverManagerDataSource dataSource, String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private static JdbcTemplate seedMonthlyVersions(DriverManagerDataSource dataSource) {
        migrateTo(dataSource, "93");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into address default values");
        Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
        jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
        Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
        jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                + "effective_from, created_by_id, created_at) values (5, 0.0500, 30, 2, date '2020-01-01', ?, now())", personId);
        jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                + "effective_from, created_by_id, created_at) values (5, 0.0400, 30, 2, date '2021-01-01', ?, now())", personId);
        migrateTo(dataSource, "107");
        jdbc.update("update configuration_version set config_values = config_values || jsonb_build_object('lateFeeChargingEnabled', true) where scope = 'LATE_PAYMENT'");
        return jdbc;
    }

    @Test
    @Tag("D-330")
    @Tag("D-210")
    @Tag("D-382")
    void migrationConvertsMonthlyToDailyRateAndDisablesCharging() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = seedMonthlyVersions(dataSource);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and config_values ->> 'lateFeeChargingEnabled' = 'true'", Long.class))
                    .isEqualTo(2L);
            Long versions = jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT'", Long.class);

            migrateTo(dataSource, "110");

            List<Map<String, Object>> rates = jdbc.queryForList("select (config_values -> 'dailyLateFeeRate' ->> 'fraction')::numeric as rate from configuration_version "
                    + "where scope = 'LATE_PAYMENT' order by effective_from");
            assertThat(rates).extracting(row -> (BigDecimal) row.get("rate")).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .containsExactly(new BigDecimal("0.0017"), new BigDecimal("0.0013"));
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and config_values ->> 'lateFeeChargingEnabled' = 'false'", Long.class))
                    .isEqualTo(versions);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and jsonb_exists(config_values, 'monthlyLateFeeRate')", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from configuration_version_status_change c join configuration_version v on v.id = c.configuration_version_id "
                    + "where v.scope = 'LATE_PAYMENT' and c.reason like 'D-330%'", Long.class)).isEqualTo(versions);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'late_fee_charge' and column_name in "
                    + "('month_ordinal', 'monthly_rate')", Long.class)).isZero();
        }
    }

    @Test
    @Tag("D-330")
    void migrationFailsWhenThereAreMonthlyLateFeeChargesToConvert() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = seedMonthlyVersions(dataSource);
            Long versionId = jdbc.queryForObject("select min(id) from configuration_version where scope = 'LATE_PAYMENT'", Long.class);
            jdbc.execute("alter table late_fee_charge drop constraint fk_late_fee_charge_debit_note");
            jdbc.execute("alter table late_fee_charge drop constraint fk_late_fee_charge_origin");
            jdbc.update("insert into late_fee_charge (debit_note_id, origin_invoice_id, student_current_account_id, month_ordinal, fact_date, late_payment_version_id, "
                    + "monthly_rate, base_amount, currency, created_at) values (2, 1, 1, 1, date '2027-03-18', ?, 0.05, 80000, 'ARS', now())", versionId);

            assertThatThrownBy(() -> migrateTo(dataSource, "110")).hasMessageContaining("late_fee_charge tiene recargos mensuales");
        }
    }
}
