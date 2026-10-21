package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para cargar una corrección de baja con la forma vieja antes de la migración de las correcciones por fecha de pago")
class LateFeeCorrectionByPaymentDateMigrationTest {
    private static final String COLUMNS = "late_fee_charge_id, debit_note_id, student_current_account_id, credit_note_id, first_corrected_day, last_corrected_day, "
            + "corrected_days, cause, enrollment_end_date, created_at";

    private static JdbcTemplate migrateTo(PostgreSQLContainer<?> postgres, String target) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private static void insertWithoutForeignKeys(JdbcTemplate jdbc, String sql) {
        jdbc.execute((java.sql.Connection connection) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set session_replication_role = replica");
                try {
                    statement.executeUpdate(sql);
                } finally {
                    statement.execute("set session_replication_role = origin");
                }
            }
            return null;
        });
    }

    private static String withdrawalCorrection(int creditNoteId, int chargeId, String firstDay, String endDate) {
        return "insert into late_fee_charge_correction (" + COLUMNS + ") values (" + chargeId + ", 1, 1, " + creditNoteId + ", '" + firstDay + "', '2027-03-31', 3, "
                + "'BACKDATED_WITHDRAWAL', " + endDate + ", now())";
    }

    private static String paymentCorrection(int creditNoteId, int chargeId, String cause, String firstDay, String allocationId, String paymentDate) {
        return "insert into late_fee_charge_correction (" + COLUMNS + ", settlement_allocation_id, payment_date) values (" + chargeId + ", 1, 1, " + creditNoteId + ", '" + firstDay
                + "', '2027-03-31', 3, '" + cause + "', null, now(), " + allocationId + ", " + paymentDate + ")";
    }

    @Test
    @Tag("D-378")
    @Tag("D-375")
    void migrationGeneralizesCorrectionCausesAndKeepsWithdrawalRows() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            JdbcTemplate jdbc = migrateTo(postgres, "127");
            insertWithoutForeignKeys(jdbc, withdrawalCorrection(100, 10, "2027-03-29", "'2027-03-28'"));
            String before = jdbc.queryForObject("select id || ':' || first_corrected_day || ':' || corrected_days || ':' || cause || ':' || enrollment_end_date from late_fee_charge_correction",
                    String.class);
            assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where cause <> 'BACKDATED_WITHDRAWAL'", Long.class)).isZero();

            jdbc = migrateTo(postgres, "129");

            assertThat(jdbc.queryForObject("select id || ':' || first_corrected_day || ':' || corrected_days || ':' || cause || ':' || enrollment_end_date from late_fee_charge_correction",
                    String.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where settlement_allocation_id is not null or payment_date is not null", Long.class)).isZero();
            final JdbcTemplate migrated = jdbc;
            insertWithoutForeignKeys(migrated, paymentCorrection(101, 11, "PAYMENT_DATED_BEFORE_CHARGED_DAYS", "2027-03-29", "55", "'2027-03-28'"));
            insertWithoutForeignKeys(migrated, paymentCorrection(102, 12, "PAYMENT_WITHIN_GRACE", "2027-03-29", "55", "'2027-03-20'"));
            assertThat(migrated.queryForObject("select count(*) from late_fee_charge_correction where cause like 'PAYMENT_%'", Long.class)).isEqualTo(2L);
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, paymentCorrection(103, 13, "PAYMENT_DATED_BEFORE_CHARGED_DAYS", "2027-03-29", "null", "'2027-03-28'")))
                    .hasMessageContaining("ck_late_fee_charge_correction_reference_by_cause");
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, paymentCorrection(104, 14, "PAYMENT_WITHIN_GRACE", "2027-03-29", "55", "null")))
                    .hasMessageContaining("ck_late_fee_charge_correction_reference_by_cause");
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, withdrawalCorrection(105, 15, "2027-03-29", "null")))
                    .hasMessageContaining("ck_late_fee_charge_correction");
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, withdrawalCorrection(106, 16, "2027-03-28", "'2027-03-28'")))
                    .hasMessageContaining("ck_late_fee_charge_correction_after_end");
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, paymentCorrection(107, 17, "PAYMENT_DATED_BEFORE_CHARGED_DAYS", "2027-03-28", "55", "'2027-03-28'")))
                    .hasMessageContaining("ck_late_fee_charge_correction_after_payment");
            assertThatThrownBy(() -> insertWithoutForeignKeys(migrated, paymentCorrection(108, 18, "PAYMENT_UNKNOWN", "2027-03-29", "55", "'2027-03-28'")))
                    .hasMessageContaining("ck_late_fee_charge_correction_cause");
            assertThat(migrated.queryForObject("select count(*) from late_fee_charge_correction", Long.class)).isEqualTo(3L);
        }
    }
}
