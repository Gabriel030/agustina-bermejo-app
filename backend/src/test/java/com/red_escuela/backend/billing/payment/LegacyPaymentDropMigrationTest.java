package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Migrates a real database through Flyway to the version before and after the legacy payment drop")
class LegacyPaymentDropMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration";
    private static final String PREVIOUS_VERSION = "121";

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

    private static Long insertAccount(JdbcTemplate jdbc, Long personId) {
        Long institutionId = jdbc.queryForObject("select max(id) from institution", Long.class);
        if (institutionId == null) {
            institutionId = insertAndReturnId(jdbc, "institution", "insert into institution (name) values ('Instituto sembrado')");
        }
        Long studentId = insertAndReturnId(jdbc, "student", "insert into student (person_id, institution_id) values (?, ?)", personId, institutionId);
        return insertAndReturnId(jdbc, "student_current_account", "insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, "
                + "balance_side, unallocated_credit_amount, unallocated_credit_amount_currency, last_entry_sequence, opened_at, version) "
                + "values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', 0, now(), 0)", studentId);
    }

    private static Long insertPayment(JdbcTemplate jdbc, Long accountId, Long personId) {
        return insertAndReturnId(jdbc, "payment", "insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, "
                + "refunded_amount, refunded_amount_currency, registered_at, started_at, method_details, registered_by_person_id) "
                + "values (?, 'ARS', 'PENDING', 'MANUAL', 'BANK_TRANSFER', 100.00, 'ARS', 0, 'ARS', now(), now(), '{\"type\":\"BANK_TRANSFER\"}'::jsonb, ?)", accountId, personId);
    }

    private static void insertPaymentEntry(JdbcTemplate jdbc, Long accountId, int sequence, Long paymentId) {
        jdbc.update("insert into current_account_entry (student_current_account_id, currency, sequence_number, direction, entry_type, amount, amount_currency, "
                + "running_balance_amount, running_balance_amount_currency, running_balance_side, effective_date, recorded_at, concept, allocatable_amount, allocatable_amount_currency, payment_id) "
                + "values (?, 'ARS', ?, 'CREDIT', 'PAYMENT_RECEIVED', 100.00, 'ARS', 100.00, 'ARS', 'IN_FAVOR', current_date, now(), 'Pago', 100.00, 'ARS', ?)", accountId, sequence, paymentId);
    }

    private static void migrateToExpectingAbort(DriverManagerDataSource dataSource, String message) {
        assertThatThrownBy(() -> migrateTo(dataSource, "123"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining(message);
    }

    private static Long insertSecondAccount(JdbcTemplate jdbc) {
        Long addressId = insertAndReturnId(jdbc, "address", "insert into address default values");
        Long otherPerson = insertAndReturnId(jdbc, "person", "insert into person (name, last_name, dni, email, username, address_id) "
                + "values ('Other', 'Person', '30111223', 'other@example.com', 'other.person', ?)", addressId);
        return insertAccount(jdbc, otherPerson);
    }

    @Test
    @Tag("D-144")
    @Tag("D-376")
    void theDropAbortsWhenAnAccountEntryPointsToALegacyPayment() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, PREVIOUS_VERSION);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertAccount(jdbc, personId);
            insertPaymentEntry(jdbc, accountId, 1, 987654L);

            migrateToExpectingAbort(dataSource, "current account entr");
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name = 'legacy_payment'", Long.class)).isEqualTo(1L);
        }
    }

    @Test
    @Tag("D-144")
    @Tag("D-376")
    void theDropAbortsWhenAnAccountEntryPointsToAPaymentOfAnotherAccount() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, PREVIOUS_VERSION);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertAccount(jdbc, personId);
            Long otherAccountId = insertSecondAccount(jdbc);
            Long otherAccountPaymentId = insertPayment(jdbc, otherAccountId, personId);
            insertPaymentEntry(jdbc, accountId, 1, otherAccountPaymentId);

            migrateToExpectingAbort(dataSource, "current account entr");
        }
    }

    @Test
    @Tag("D-144")
    @Tag("D-376")
    void theDropAbortsWhenAnAllocationSourceHasNoPaymentBehindIt() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, PREVIOUS_VERSION);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertAccount(jdbc, personId);
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("set session_replication_role = replica");
                    statement.execute("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, credit_entry_id, payment_id, amount, "
                            + "amount_currency, recorded_at) values (1, " + accountId + ", 1, 555, 10.00, 'ARS', now())");
                    statement.execute("set session_replication_role = origin");
                }
                return null;
            });

            migrateToExpectingAbort(dataSource, "allocation source");
        }
    }

    @Test
    @Tag("D-144")
    @Tag("D-376")
    void theDropPassesWithValidEntriesAndTheCompositeForeignKeyRejectsOrphanAndForeignAccountPayments() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrateTo(dataSource, PREVIOUS_VERSION);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long personId = insertPerson(jdbc);
            Long accountId = insertAccount(jdbc, personId);
            Long paymentId = insertPayment(jdbc, accountId, personId);
            insertPaymentEntry(jdbc, accountId, 1, paymentId);

            migrateTo(dataSource, "123");

            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('legacy_payment', 'legacy_payment_allocation')", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname = 'fk_current_account_entry_payment'", Long.class)).isEqualTo(1L);
            assertThatThrownBy(() -> insertPaymentEntry(jdbc, accountId, 2, 987654L))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_current_account_entry_payment");
            Long otherAccountId = insertSecondAccount(jdbc);
            Long otherAccountPaymentId = insertPayment(jdbc, otherAccountId, personId);
            assertThatThrownBy(() -> insertPaymentEntry(jdbc, accountId, 2, otherAccountPaymentId))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_current_account_entry_payment");
        }
    }
}
