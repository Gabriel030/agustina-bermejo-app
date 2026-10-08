package com.red_escuela.backend.billing.invoice.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-056")
@Tag("integration")
class V91MigrationOnLegacyDataIT {

    @Test
    void theLegacyInvoiceKeepsItsRowsAndIncomingForeignKeysAndTheNewTablesStartEmpty() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("90").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into invoice (fee_type, period, due_date, base_amount, base_amount_currency, status, created_at, updated_at, issue_reason) "
                    + "values ('RECURRING_FEE', date '2026-09-01', date '2026-09-10', 48000.00, 'ARS', 'PENDING', now(), now(), 'LEGACY')");
            Long invoiceId = jdbc.queryForObject("select max(id) from invoice", Long.class);
            jdbc.update("insert into invoice_adjustment (invoice_id, type, value, resolved_base, reason, applied_at) "
                    + "values (?, 'SURCHARGE', '{\"type\":\"PERCENTAGE\",\"percentage\":0.1000}'::jsonb, 48000.00, 'Mora', now())", invoiceId);
            Integer incomingBefore = jdbc.queryForObject("select count(*) from pg_constraint where contype = 'f' and confrelid = 'invoice'::regclass", Integer.class);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("95").load().migrate();

            assertThat(incomingBefore).isGreaterThan(3);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where contype = 'f' and confrelid = 'legacy_invoice'::regclass", Integer.class))
                    .isEqualTo(incomingBefore);
            assertThat(jdbc.queryForObject("select base_amount from legacy_invoice where id = ?", BigDecimal.class, invoiceId)).isEqualByComparingTo("48000.00");
            assertThat(jdbc.queryForObject("select count(*) from invoice_adjustment a join legacy_invoice i on i.id = a.invoice_id", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from invoice", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from invoice_line", Integer.class)).isZero();
        }
    }
}
