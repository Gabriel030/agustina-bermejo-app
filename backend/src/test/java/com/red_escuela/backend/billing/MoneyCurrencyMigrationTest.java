package com.red_escuela.backend.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.persistence.MonetaryAdjustmentConverter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

class MoneyCurrencyMigrationTest {

    @Test
    void existingBillingDataIsReadAfterTheMigrationWithTheSameValuesAndArsCurrency() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("78").load().migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) "
                    + "values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into invoice (fee_type, period, due_date, base_amount, status, created_at, updated_at) "
                    + "values ('RECURRING_FEE', date '2026-09-01', date '2026-09-10', 48000.00, 'PENDING', now(), now())");
            Long invoiceId = jdbc.queryForObject("select max(id) from invoice", Long.class);
            jdbc.update("insert into invoice_adjustment (invoice_id, type, value, resolved_base, reason, applied_at) "
                    + "values (?, 'SURCHARGE', '{\"type\":\"PERCENTAGE\",\"percentage\":0.1000}'::jsonb, 48000.00, 'Mora', now())", invoiceId);
            jdbc.update("insert into invoice_adjustment (invoice_id, type, value, resolved_base, reason, applied_at) "
                    + "values (?, 'DISCOUNT', '{\"type\":\"FIXED\",\"amount\":5000.00}'::jsonb, 48000.00, 'Descuento', now())", invoiceId);
            jdbc.update("insert into payment (amount, method, registered_by_id, paid_at, status, created_at, updated_at) "
                    + "values (1500.50, 'CASH', ?, now(), 'CONFIRMED', now(), now())", personId);
            jdbc.update("insert into collections_policy (grace_period_days, surcharge_percentage, suspension_after_days, "
                    + "suspension_after_invoice_count, withdrawal_after_days, withdrawal_after_invoice_count, effective_from, "
                    + "created_by_id, created_at) values (10, 0.1000, 30, 2, 60, 3, date '2026-01-01', ?, now())", personId);
            jdbc.update("insert into institution (name) values ('Instituto sembrado')");
            Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
            jdbc.update("insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
            Long studentId = jdbc.queryForObject("select max(id) from student", Long.class);
            jdbc.update("insert into term (year_number, status) values (2026, true)");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into course (name, term_id) values ('Curso sembrado', ?)", termId);
            Long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
            jdbc.update("insert into course_enrollment (course_id, student_id, status) values (?, ?, 'ENROLLED')", courseId, studentId);
            Long enrollmentId = jdbc.queryForObject("select max(id) from course_enrollment", Long.class);
            jdbc.update("insert into scholarship (course_enrollment_id, value, reason, status, granted_by_id, granted_at) "
                    + "values (?, '{\"type\":\"FIXED\",\"amount\":2000.00}'::jsonb, 'Beca fija', 'ACTIVE', ?, now())", enrollmentId, personId);
            jdbc.update("insert into scholarship (course_enrollment_id, value, reason, status, granted_by_id, granted_at) "
                    + "values (?, '{\"type\":\"PERCENTAGE\",\"percentage\":0.3000}'::jsonb, 'Beca porcentual', 'CANCELLED', ?, now())", enrollmentId, personId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select base_amount_currency from legacy_invoice where id = ?", String.class, invoiceId)).isEqualTo("ARS");
            assertThat(jdbc.queryForObject("select base_amount from legacy_invoice where id = ?", BigDecimal.class, invoiceId))
                    .isEqualByComparingTo("48000.00");
            Map<String, Object> payment = jdbc.queryForMap("select amount, amount_currency from payment");
            assertThat(payment.get("amount_currency")).isEqualTo("ARS");
            assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo("1500.50");
            assertThat(jdbc.queryForObject("select resolved_base_currency from invoice_adjustment limit 1", String.class)).isEqualTo("ARS");
            assertThat(jdbc.queryForObject("select surcharge_percentage from collections_policy", BigDecimal.class))
                    .isEqualByComparingTo("0.1");
            assertThat(jdbc.queryForObject("select currency from course limit 1", String.class)).isNotNull();

            MonetaryAdjustmentConverter converter = new MonetaryAdjustmentConverter(new ObjectMapper());
            String surcharge = jdbc.queryForObject("select value::text from invoice_adjustment where type = 'SURCHARGE'", String.class);
            String discount = jdbc.queryForObject("select value::text from invoice_adjustment where type = 'DISCOUNT'", String.class);
            String fixedScholarship = jdbc.queryForObject("select value::text from scholarship where status = 'ACTIVE'", String.class);
            String proportionalScholarship = jdbc.queryForObject("select value::text from scholarship where status = 'CANCELLED'", String.class);

            MonetaryAdjustment readSurcharge = converter.convertToEntityAttribute(surcharge);
            assertThat(readSurcharge.applyTo(ars(48000))).isEqualTo(ars(4800));
            assertThat(converter.convertToEntityAttribute(discount)).isEqualTo(new MonetaryAdjustment.Nominal(ars(5000)));
            assertThat(converter.convertToEntityAttribute(fixedScholarship)).isEqualTo(new MonetaryAdjustment.Nominal(ars(2000)));
            assertThat(converter.convertToEntityAttribute(proportionalScholarship).applyTo(ars(10000))).isEqualTo(ars(3000));
        }
    }
}
