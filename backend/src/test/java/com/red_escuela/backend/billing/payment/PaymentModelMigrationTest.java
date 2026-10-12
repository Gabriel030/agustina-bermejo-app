package com.red_escuela.backend.billing.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentModelMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration";

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations(LOCATIONS).target(target).load().migrate();
    }

    private static Long insertAndReturnId(JdbcTemplate jdbc, String table, String insertSql, Object... args) {
        jdbc.update(insertSql, args);
        return jdbc.queryForObject("select max(id) from " + table, Long.class);
    }

    private static Long insertPerson(JdbcTemplate jdbc) {
        Long addressId = insertAndReturnId(jdbc, "address", "insert into address default values");
        return insertAndReturnId(jdbc, "person", "insert into person (name, last_name, dni, email, username, address_id) "
                + "values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
    }

    private static Long insertStudentCurrentAccount(JdbcTemplate jdbc, Long personId, String currency) {
        Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
        if (institutionId == null) {
            institutionId = insertAndReturnId(jdbc, "institution", "insert into institution (name) values ('Instituto sembrado')");
        }
        Long studentId = insertAndReturnId(jdbc, "student", "insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
        return insertAndReturnId(jdbc, "student_current_account", "insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, "
                + "balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at, version) "
                + "values (?, ?, 0, ?, 'ZERO', 0, ?, 0, now(), 0)", studentId, currency, currency, currency);
    }

    private static void insertPayment(JdbcTemplate jdbc, Long accountId, Long personId, String overrides) {
        Map<String, String> columns = new java.util.LinkedHashMap<>(Map.ofEntries(
                Map.entry("student_current_account_id", String.valueOf(accountId)), Map.entry("currency", "'ARS'"), Map.entry("status", "'PENDING'"),
                Map.entry("gateway", "'MANUAL'"), Map.entry("method_type", "'BANK_TRANSFER'"), Map.entry("amount", "100.00"),
                Map.entry("amount_currency", "'ARS'"), Map.entry("refunded_amount", "0"), Map.entry("refunded_amount_currency", "'ARS'"),
                Map.entry("registered_at", "now()"), Map.entry("started_at", "now()"), Map.entry("method_details", "'{\"type\":\"BANK_TRANSFER\"}'::jsonb"),
                Map.entry("registered_by_person_id", String.valueOf(personId))));
        for (String override : overrides.isBlank() ? new String[0] : overrides.split(";")) {
            String[] pair = override.split("=", 2);
            columns.put(pair[0], pair[1]);
        }
        jdbc.update("insert into payment (" + String.join(", ", columns.keySet()) + ") values (" + String.join(", ", columns.values()) + ")");
    }

    @Test
    @Tag("D-082")
    @Tag("D-144")
    void renamingThePaymentTableKeepsEveryLegacyRowAmountAndForeignKeyIntact() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "102");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            jdbc.update("insert into payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at, external_reference) "
                    + "values (1500.50, 'ARS', 'CASH', ?, timestamp '2026-09-01 10:00:00', 'CONFIRMED', now(), now(), null)", personId);
            jdbc.update("insert into payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at, external_reference) "
                    + "values (80000.00, 'ARS', 'MERCADO_PAGO', ?, timestamp '2026-09-02 10:00:00', 'CONFIRMED', now(), now(), 'mp-123')", personId);
            jdbc.update("insert into payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at, external_reference) "
                    + "values (10.00, 'USD', 'TRANSFER', ?, timestamp '2026-09-03 10:00:00', 'REJECTED', now(), now(), null)", personId);
            migrateTo(dataSource, "103");
            List<Map<String, Object>> rowsBefore = jdbc.queryForList("select * from payment order by id");
            BigDecimal sumBefore = jdbc.queryForObject("select sum(amount) from payment", BigDecimal.class);
            List<String> foreignKeysBefore = jdbc.queryForList("select conname from pg_constraint where contype = 'f' and confrelid = 'payment'::regclass order by conname", String.class);

            migrateTo(dataSource, "104");

            assertThat(jdbc.queryForList("select * from legacy_payment order by id")).isEqualTo(rowsBefore);
            assertThat(jdbc.queryForObject("select sum(amount) from legacy_payment", BigDecimal.class)).isEqualByComparingTo(sumBefore);
            assertThat(foreignKeysBefore).isNotEmpty();
            assertThat(jdbc.queryForList("select conname from pg_constraint where contype = 'f' and confrelid = 'legacy_payment'::regclass order by conname", String.class))
                    .isEqualTo(foreignKeysBefore);
            assertThat(jdbc.queryForObject("select count(*) from payment", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from legacy_payment where external_reference = 'mp-123'", Long.class)).isEqualTo(1);
            jdbc.update("insert into legacy_payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at) "
                    + "values (1, 'ARS', 'CASH', ?, now(), 'CONFIRMED', now(), now())", personId);
            assertThat(jdbc.queryForObject("select count(*) from legacy_payment", Long.class)).isEqualTo(4);
        }
    }

    @Test
    @Tag("D-082")
    @Tag("D-144")
    void theFirstNewPaymentGetsAnIdAboveEveryLegacyPaymentId() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "102");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            for (int index = 0; index < 3; index++) {
                jdbc.update("insert into payment (amount, amount_currency, method, registered_by_id, paid_at, status, created_at, updated_at) "
                        + "values (10.00, 'ARS', 'CASH', ?, now(), 'CONFIRMED', now(), now())", personId);
            }
            Long maxLegacyId = jdbc.queryForObject("select max(id) from payment", Long.class);

            migrateTo(dataSource, "104");
            Long accountId = insertStudentCurrentAccount(jdbc, personId, "ARS");
            insertPayment(jdbc, accountId, personId, "");

            assertThat(jdbc.queryForObject("select min(id) from payment", Long.class)).isGreaterThan(maxLegacyId);
        }
    }

    @Test
    @Tag("D-082")
    void theNewPaymentIdentityStartsAtOneWhenThereAreNoLegacyPayments() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "104");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            insertPayment(jdbc, insertStudentCurrentAccount(jdbc, personId, "ARS"), personId, "");

            assertThat(jdbc.queryForObject("select min(id) from payment", Long.class)).isEqualTo(1L);
        }
    }

    @Test
    @Tag("D-082")
    @Tag("D-097")
    @Tag("D-136")
    void theNewPaymentTableEnforcesItsInvariantsInTheDatabase() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "104");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertStudentCurrentAccount(jdbc, personId, "ARS");

            insertPayment(jdbc, accountId, personId, "idempotency_key='k-1'");
            insertPayment(jdbc, accountId, personId, "idempotency_key='k-2';gateway='MERCADO_PAGO';gateway_payment_id='mp-1';method_type='CARD'");
            insertPayment(jdbc, accountId, personId, "status='APPROVED';approved_amount=100.00;approved_amount_currency='ARS';paid_on=date '2027-03-01'");

            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "amount=0")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "amount_currency='USD'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "currency='USD';amount_currency='USD';refunded_amount_currency='USD'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "refunded_amount=100.01")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "idempotency_key='k-1'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "gateway='MERCADO_PAGO';gateway_payment_id='mp-1'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "status='APPROVED';approved_amount=100.00;approved_amount_currency='ARS'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "status='UNKNOWN'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, accountId, personId, "registered_by_process='JOB'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertPayment(jdbc, 999999L, personId, "")).isInstanceOf(DataIntegrityViolationException.class);
            insertPayment(jdbc, accountId, personId, "gateway='DLOCAL';gateway_payment_id='mp-1'");
            insertPayment(jdbc, accountId, personId, "");
            insertPayment(jdbc, accountId, personId, "");
            assertThat(jdbc.queryForObject("select count(*) from payment", Long.class)).isEqualTo(6);
        }
    }

    @Test
    @Tag("D-082")
    void thePaymentStatusHistoryAndTheIdempotencyRecordsAreStructuredAsDeclared() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, "104");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertStudentCurrentAccount(jdbc, personId, "ARS");
            insertPayment(jdbc, accountId, personId, "");
            Long paymentId = jdbc.queryForObject("select max(id) from payment", Long.class);
            jdbc.update("insert into payment_status_change (payment_id, event, from_status, to_status, actor_person_id, occurred_at) "
                    + "values (?, 'PENDING_BANK_TRANSFER_CREATED', null, 'PENDING', ?, now())", paymentId, personId);

            assertThatThrownBy(() -> jdbc.update("update payment_status_change set reason = 'edited'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("delete from payment_status_change")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into payment_status_change (payment_id, event, to_status, occurred_at) values (?, 'X', 'PENDING', now())", paymentId))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('k', 'REGISTER_PAYMENT', 'h', now())");
            assertThatThrownBy(() -> jdbc.update("insert into idempotency_record (idempotency_key, operation_type, request_hash, created_at) values ('k', 'REGISTER_PAYMENT', 'h', now())"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update idempotency_record set response_status = 201"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("update idempotency_record set response_status = 201, response_body = '{}'::jsonb");
        }
    }
}
