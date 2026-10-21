package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Migrates a real database through Flyway step by step to prove the legacy scholarship tables are dropped only when no legacy scholarship is active")
@Tag("D-407")
@Tag("D-384")
class LegacyScholarshipDropMigrationTest {

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }

    private record LegacyScholarship(long id, long installmentId, long invoiceDiscountId) {
    }

    private static LegacyScholarship insertLegacyScholarshipWithAnAppliedInstallment(JdbcTemplate jdbc, String status) {
        Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
        String username = "legacy" + System.nanoTime();
        Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values (?, ?, ?, 'ACTIVE') returning id",
                Long.class, username, username + "@example.com", addressId);
        Long institutionId = jdbc.queryForObject("insert into institution (name) values (?) returning id", Long.class, "Inst " + username);
        Long studentId = jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id",
                Long.class, personId, institutionId);
        Long termId = jdbc.queryForObject("insert into term (year_number, status) values (2026, true) returning id", Long.class);
        Long courseId = jdbc.queryForObject("insert into course (name, term_id, currency) values (?, ?, 'ARS') returning id", Long.class, "Curso " + username, termId);
        Long enrollmentId = jdbc.queryForObject("insert into course_enrollment (course_id, student_id, status) values (?, ?, 'ENROLLED') returning id",
                Long.class, courseId, studentId);
        Long scholarshipId = jdbc.queryForObject("""
                insert into scholarship (course_enrollment_id, value, reason, status, granted_by_id, granted_at, applies_to)
                values (?, cast('{"type":"PERCENTAGE","percentage":0.3000}' as jsonb), 'Beca', ?, ?, now(), 'INSTALLMENTS') returning id
                """, Long.class, enrollmentId, status, personId);
        Long installmentId = jdbc.queryForObject("insert into scholarship_installment (scholarship_id, status) values (?, 'APPLIED') returning id", Long.class, scholarshipId);
        Long accountId = jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """, Long.class, studentId);
        Long invoiceId = jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status,
                    lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'DRAFT', 100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now())
                returning id
                """, Long.class, accountId);
        Long lineId = jdbc.queryForObject("""
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount,
                    discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency)
                values (?, 'ARS', 1, 'ADMINISTRATIVE_FEE', 'Tasa', date '2026-03-01', ?, 100, 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS') returning id
                """, Long.class, invoiceId, "K" + username);
        Long discountId = jdbc.queryForObject("""
                insert into invoice_discount (invoice_line_id, discount_type, concept, currency, base_amount, base_amount_currency, discount_amount, discount_amount_currency,
                    student_discount_id, source_installment_id)
                values (?, 'SCHOLARSHIP', 'Beca', 'ARS', 100, 'ARS', 30, 'ARS', ?, ?) returning id
                """, Long.class, lineId, scholarshipId, installmentId);
        jdbc.update("update invoice set document_status = 'ISSUED', payment_status = 'PENDING', invoice_number = ?, issue_date = date '2026-03-03', "
                + "due_date = date '2026-03-13', student_snapshot = cast('{\"fullName\":\"Ana\"}' as jsonb) where id = ?", "F-" + (System.nanoTime() % 1_000_000_000L), invoiceId);
        return new LegacyScholarship(scholarshipId, installmentId, discountId);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, table);
    }

    @Test
    void theMigrationFailsWhenAnActiveLegacyScholarshipExists() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "130");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertLegacyScholarshipWithAnAppliedInstallment(jdbc, "ACTIVE");

            assertThatThrownBy(() -> migrateTo(dataSource, "132")).hasMessageContaining("ACTIVE legacy scholarships");

            assertThat(tableExists(jdbc, "scholarship")).isTrue();
            assertThat(tableExists(jdbc, "scholarship_installment")).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'invoice_discount' and column_name = 'source_installment_id'", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    void theMigrationDropsTheLegacyTablesWhenNoneIsActive() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "130");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            LegacyScholarship legacy = insertLegacyScholarshipWithAnAppliedInstallment(jdbc, "INACTIVE");

            migrateTo(dataSource, "132");

            assertThat(tableExists(jdbc, "scholarship")).isFalse();
            assertThat(tableExists(jdbc, "scholarship_installment")).isFalse();
            assertThat(jdbc.queryForObject("select discount_amount from invoice_discount where id = ?", BigDecimal.class, legacy.invoiceDiscountId())).isEqualByComparingTo("30");
            assertThat(jdbc.queryForObject("select student_discount_id from invoice_discount where id = ?", Long.class, legacy.invoiceDiscountId())).isNull();
            assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname = 'trg_invoice_discount_immutable_when_issued' and tgenabled = 'O'", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    void invoiceDiscountNoLongerHasTheSourceInstallmentColumn() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "130");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'invoice_discount' and column_name = 'source_installment_id'", Long.class))
                    .isEqualTo(1L);

            migrateTo(dataSource, "132");

            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'invoice_discount' and column_name = 'source_installment_id'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname like '%source_installment%'", Long.class)).isZero();
        }
    }
}
