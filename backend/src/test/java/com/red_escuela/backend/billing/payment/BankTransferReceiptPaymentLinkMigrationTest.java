package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Migrates a real database through Flyway to the version before and after the receipt submission model")
@Tag("D-081")
class BankTransferReceiptPaymentLinkMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration";
    private static final String PREVIOUS_VERSION = "127";
    private static final String TARGET_VERSION = "130";
    private static final long INVOICE_ID = 4242L;

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations(LOCATIONS).target(target).load().migrate();
    }

    private static Long insertAndReturnId(JdbcTemplate jdbc, String table, String insertSql, Object... args) {
        jdbc.update(insertSql, args);
        return jdbc.queryForObject("select max(id) from " + table, Long.class);
    }

    private static Long insertAccountAndReturnItsPerson(JdbcTemplate jdbc, long[] accountIdHolder) {
        Long addressId = insertAndReturnId(jdbc, "address", "insert into address default values");
        Long personId = insertAndReturnId(jdbc, "person", "insert into person (name, last_name, dni, email, username, address_id) "
                + "values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
        Long institutionId = insertAndReturnId(jdbc, "institution", "insert into institution (name) values ('Instituto sembrado')");
        Long studentId = insertAndReturnId(jdbc, "student", "insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
        accountIdHolder[0] = insertAndReturnId(jdbc, "student_current_account", "insert into student_current_account (student_id, currency, balance_amount, "
                + "balance_amount_currency, balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at, version) "
                + "values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', 0, now(), 0)", studentId);
        return personId;
    }

    private static Long insertPayment(JdbcTemplate jdbc, Long accountId, Long personId, String operationNumber) {
        return insertAndReturnId(jdbc, "payment", "insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, "
                + "refunded_amount, refunded_amount_currency, registered_at, started_at, method_details, registered_by_person_id) "
                + "values (?, 'ARS', 'PENDING', 'MANUAL', 'BANK_TRANSFER', 100.00, 'ARS', 0, 'ARS', now(), now(), "
                + "jsonb_build_object('type', 'BANK_TRANSFER', 'operationNumber', cast(? as text), 'transferDate', '2027-03-09'), ?)", accountId, operationNumber, personId);
    }

    private static void insertReceipt(JdbcTemplate jdbc, Long paymentId) {
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, payment_id) values (?, 'comprobante.pdf', now(), 'PENDING_REVIEW', ?)",
                INVOICE_ID, paymentId);
    }

    private static JdbcTemplate migratedTo(DriverManagerDataSource dataSource, String version) {
        migrateTo(dataSource, version);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table bank_transfer_receipt drop constraint fk_bank_transfer_receipt_invoice");
        return jdbc;
    }

    @Test
    void migrationAbortsExplicitlyOnOrphanPaymentId() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = migratedTo(dataSource, PREVIOUS_VERSION);
            insertReceipt(jdbc, 987654L);

            assertThatThrownBy(() -> migrateTo(dataSource, TARGET_VERSION)).isInstanceOf(FlywayException.class).hasMessageContaining("V130 aborted");

            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name = 'payment_checkout_item'", Long.class)).isZero();
        }
    }

    @Test
    void historicalReceiptsWithoutPaymentSurviveTheMigration() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = migratedTo(dataSource, PREVIOUS_VERSION);
            insertReceipt(jdbc, null);

            migrateTo(dataSource, TARGET_VERSION);

            assertThat(jdbc.queryForMap("select payment_id, declared_amount, transfer_date, operation_number, origin_account, holder_name from bank_transfer_receipt"))
                    .containsEntry("payment_id", null).containsEntry("declared_amount", null).containsEntry("operation_number", null);
        }
    }

    @Test
    @Tag("D-260")
    void legacyReceiptsStayProtectedFromTwoPendingOnesAndNewReceiptsMaySharetheOldestInvoice() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = migratedTo(dataSource, PREVIOUS_VERSION);
            migrateTo(dataSource, TARGET_VERSION);
            long[] account = new long[1];
            Long personId = insertAccountAndReturnItsPerson(jdbc, account);
            Long firstPayment = insertPayment(jdbc, account[0], personId, "OP-1");
            Long secondPayment = insertPayment(jdbc, account[0], personId, "OP-2");

            insertReceipt(jdbc, null);
            assertThatThrownBy(() -> insertReceipt(jdbc, null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_bank_transfer_receipt_pending");
            insertReceipt(jdbc, firstPayment);
            insertReceipt(jdbc, secondPayment);

            assertThat(jdbc.queryForObject("select count(*) from bank_transfer_receipt where invoice_id = ?", Long.class, INVOICE_ID)).isEqualTo(3L);
        }
    }

    @Test
    @Tag("D-269")
    void theDuplicateIndexIgnoresRejectedAndCancelledPaymentsAndPaymentsWithoutDetails() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            JdbcTemplate jdbc = migratedTo(dataSource, TARGET_VERSION);
            long[] account = new long[1];
            Long personId = insertAccountAndReturnItsPerson(jdbc, account);
            Long first = insertPayment(jdbc, account[0], personId, "OP-DUP");

            assertThatThrownBy(() -> insertPayment(jdbc, account[0], personId, "OP-DUP")).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_payment_bank_transfer_duplicate");

            jdbc.update("update payment set status = 'REJECTED', rejection_reason = 'x' where id = ?", first);
            insertPayment(jdbc, account[0], personId, "OP-DUP");
            assertThat(jdbc.queryForObject("select count(*) from payment where method_details ->> 'operationNumber' = 'OP-DUP'", Long.class)).isEqualTo(2L);
        }
    }
}
