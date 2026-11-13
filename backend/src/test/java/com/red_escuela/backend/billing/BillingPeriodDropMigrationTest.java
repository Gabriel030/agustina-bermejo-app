package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class BillingPeriodDropMigrationTest {

    @Test
    void billingPeriodTableIsDroppedAndTheRestOfBillingSurvives() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("214")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(tableExists(jdbc, "billing_period")).isTrue();

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(tableExists(jdbc, "billing_period")).isFalse();
            assertThat(tableExists(jdbc, "price_version")).isTrue();
            assertThat(tableExists(jdbc, "monthly_invoice_run")).isTrue();
        }
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = current_schema() and table_name = ?""", Integer.class, table);
        return count != null && count > 0;
    }
}
