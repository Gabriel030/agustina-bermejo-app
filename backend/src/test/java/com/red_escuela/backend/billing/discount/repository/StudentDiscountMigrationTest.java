package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database through Flyway step by step to prove legacy manual discounts survive the individual discount rename")
@Tag("D-384")
@Tag("migration")
class StudentDiscountMigrationTest {

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static long insertIssuedInvoiceWithManualDiscount(JdbcTemplate jdbc, String number, int discountAmount) {
        Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
        String username = "migration" + System.nanoTime();
        Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values (?, ?, ?, 'ACTIVE') returning id",
                Long.class, username, username + "@example.com", addressId);
        Long institutionId = jdbc.queryForObject("insert into institution (name) values (?) returning id", Long.class, "Inst " + username);
        Long studentId = jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id",
                Long.class, personId, institutionId);
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
                values (?, 'ARS', 1, 'ADMINISTRATIVE_FEE', 'Tasa', date '2027-03-01', ?, 100, 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS') returning id
                """, Long.class, invoiceId, "K" + username);
        Long discountId = jdbc.queryForObject("""
                insert into invoice_discount (invoice_line_id, discount_type, concept, currency, base_amount, base_amount_currency, discount_amount, discount_amount_currency)
                values (?, 'MANUAL', 'Manual', 'ARS', 100, 'ARS', ?, 'ARS') returning id
                """, Long.class, lineId, discountAmount);
        jdbc.update("update invoice set document_status = 'ISSUED', payment_status = 'PENDING', invoice_number = ?, issue_date = date '2027-03-03', "
                + "due_date = date '2027-03-13', student_snapshot = cast('{\"fullName\":\"Ana\"}' as jsonb) where id = ?", number, invoiceId);
        return discountId;
    }

    @Test
    void everyManualDiscountOfAnIssuedInvoiceBecomesIndividualWithoutLosingAnAmount() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("117")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            long first = insertIssuedInvoiceWithManualDiscount(jdbc, "F-00000001", 10);
            long second = insertIssuedInvoiceWithManualDiscount(jdbc, "F-00000002", 25);

            migrateTo(dataSource, "118");

            assertThat(jdbc.queryForList("select discount_type from invoice_discount where id in (?, ?) order by id", String.class, first, second))
                    .containsExactly("INDIVIDUAL", "INDIVIDUAL");
            assertThat(jdbc.queryForObject("select sum(discount_amount) from invoice_discount", java.math.BigDecimal.class)).isEqualByComparingTo("35");
            assertThat(jdbc.queryForObject("select count(*) from invoice_discount where discount_type = 'MANUAL'", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname = 'trg_invoice_discount_immutable_when_issued' and tgenabled = 'O'", Long.class))
                    .isEqualTo(1L);
        }
    }
}
