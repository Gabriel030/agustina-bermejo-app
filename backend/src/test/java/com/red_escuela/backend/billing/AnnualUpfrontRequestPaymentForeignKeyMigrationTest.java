package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para comprobar el chequeo de huerfanos y la clave foranea")
@Tag("migration")
class AnnualUpfrontRequestPaymentForeignKeyMigrationTest {

    private static final String INSERT_REQUEST = """
            insert into annual_upfront_request (program_enrollment_id, lective_year_term_id, invoice_id, status, payment_id,
                requested_by_person_id, requested_at, accepted_terms_version, accepted_terms_text, accepted_at, accepted_by_person_id)
            values (9001, 9002, 9003, 'PAYMENT_UNDER_REVIEW', ?, 9004, now(), 'v1', 'terms', now(), 9004)""";

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void insertRequestWithoutForeignKeyChecks(DriverManagerDataSource dataSource, Long paymentId) {
        new JdbcTemplate(dataSource).execute((Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set session_replication_role = replica");
                try (PreparedStatement insert = connection.prepareStatement(INSERT_REQUEST)) {
                    insert.setObject(1, paymentId);
                    insert.executeUpdate();
                }
                statement.execute("set session_replication_role = origin");
            }
            return null;
        });
    }

    private static void insertPaymentWithoutForeignKeyChecks(DriverManagerDataSource dataSource) {
        new JdbcTemplate(dataSource).execute((Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set session_replication_role = replica");
                statement.executeUpdate("""
                        insert into payment (id, student_current_account_id, currency, status, gateway, method_type, amount, amount_currency,
                            refunded_amount_currency, registered_by_process, registered_at, started_at, method_details)
                        values (5001, 9100, 'ARS', 'PENDING', 'MANUAL', 'CASH', 100, 'ARS', 'ARS', 'TEST', now(), now(), '{}'::jsonb)""");
                statement.execute("set session_replication_role = origin");
            }
            return null;
        });
    }

    private static Integer validatedForeignKeyCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                select count(*) from pg_constraint
                where conname = 'fk_annual_upfront_request_payment' and contype = 'f' and convalidated""", Integer.class);
    }

    @Test
    void foreignKeyIsAddedAndValidatedOnEmptyDatabase() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("341")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            assertThat(validatedForeignKeyCount(jdbc)).isZero();

            migrateTo(database, "371");

            assertThat(validatedForeignKeyCount(jdbc)).isEqualTo(1);
        }
    }

    @Test
    void foreignKeyReferencesPaymentIdentifier() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("371")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);

            String definition = jdbc.queryForObject(
                    "select pg_get_constraintdef(oid) from pg_constraint where conname = 'fk_annual_upfront_request_payment'", String.class);

            assertThat(definition).isEqualTo("FOREIGN KEY (payment_id) REFERENCES payment(id)");
        }
    }

    @Test
    void orphanPaymentReferenceAbortsTheMigrationWithAClearMessageAndKeepsTheRow() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("341")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertRequestWithoutForeignKeyChecks(database, 987654L);

            assertThatThrownBy(() -> migrateTo(database, "371"))
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("annual_upfront_request has 1 row(s) whose payment_id does not exist in payment")
                    .hasMessageContaining("fk_annual_upfront_request_payment");

            assertThat(jdbc.queryForObject("select count(*) from annual_upfront_request where payment_id = 987654", Integer.class)).isEqualTo(1);
            assertThat(validatedForeignKeyCount(jdbc)).isZero();
        }
    }

    @Test
    void requestPointingToAnExistingPaymentPassesTheCheckAndTheForeignKeyIsValidated() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("341")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertPaymentWithoutForeignKeyChecks(database);
            insertRequestWithoutForeignKeyChecks(database, 5001L);

            migrateTo(database, "371");

            assertThat(validatedForeignKeyCount(jdbc)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select payment_id from annual_upfront_request", Long.class)).isEqualTo(5001L);
        }
    }

    @Test
    void requestsWithoutPaymentDoNotBlockTheMigration() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("341")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            insertRequestWithoutForeignKeyChecks(database, null);

            migrateTo(database, "371");

            assertThat(validatedForeignKeyCount(jdbc)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from annual_upfront_request", Integer.class)).isEqualTo(1);
        }
    }
}
