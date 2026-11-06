package com.red_escuela.backend.graduation.certificate.regular;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class RegularStudentCertificateMigrationTest {

    private static final String INSERT_REQUEST = "insert into regular_certificate_request (student_id, kind, program_enrollment_id, "
            + "course_enrollment_id, requested_by_id, requested_at, channel, outcome) values (1, 'PROGRAM', ?, ?, 1, now(), 'STAFF', 'ISSUED')";
    private static final String INSERT_CERTIFICATE = "insert into regular_student_certificate (code, kind, program_enrollment_id, "
            + "course_enrollment_id, student_id, status, issued_at, expires_at, validity_days, issued_by_id, issue_channel, request_id, "
            + "billing_mode_at_issue, standing_basis, standing_confirmed_by_id, standing_confirmed_at, standing_evidence, snapshot, "
            + "template_ref, pdf_sha256, pdf_path) values (?, 'PROGRAM', ?, ?, 1, ?, now(), now(), 30, 1, 'STAFF', ?, 'OFF', ?, ?, ?, ?, "
            + "'{}'::jsonb, 'REGULAR_BUILT_IN_V1', repeat('a', 64), 'x.pdf')";

    private JdbcTemplate migratedJdbc(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table regular_certificate_request disable trigger all");
        jdbc.execute("alter table regular_student_certificate disable trigger all");
        return jdbc;
    }

    private long insertRequest(JdbcTemplate jdbc, Long programEnrollmentId, Long courseEnrollmentId) {
        jdbc.update(INSERT_REQUEST, programEnrollmentId, courseEnrollmentId);
        return jdbc.queryForObject("select max(id) from regular_certificate_request", Long.class);
    }

    private void insertCertificate(JdbcTemplate jdbc, String code, Long programEnrollmentId, Long courseEnrollmentId, String status,
                                   long requestId, String basis, Long confirmedBy) {
        jdbc.update(INSERT_CERTIFICATE, code, programEnrollmentId, courseEnrollmentId, status, requestId, basis, confirmedBy,
                confirmedBy == null ? null : java.sql.Timestamp.valueOf("2026-11-05 12:00:00"), confirmedBy == null ? null : "recibo 1234");
    }

    @Test
    void migrationCreatesTablesOverAnExistingSchemaAndKeepsEarlierData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("190")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            int certificatesBefore = jdbc.queryForObject("select count(*) from graduation_certificate", Integer.class);

            JdbcTemplate migrated = migratedJdbc(dataSource);

            assertThat(migrated.queryForObject("select count(*) from graduation_certificate", Integer.class)).isEqualTo(certificatesBefore);
            assertThat(migrated.queryForObject("select count(*) from regular_student_certificate", Integer.class)).isZero();
            assertThat(migrated.queryForObject("select count(*) from regular_certificate_request", Integer.class)).isZero();
        }
    }

    @Test
    void requestAndCertificateMustReferenceExactlyOneEnrollment() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("190")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insertRequest(jdbc, null, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertRequest(jdbc, 1L, 1L)).isInstanceOf(DataIntegrityViolationException.class);
            long requestId = insertRequest(jdbc, 1L, null);
            assertThatThrownBy(() -> insertCertificate(jdbc, "AAAAAAAAAAAAAAAAAAAAAAAAAA", null, null, "VALID", requestId, "BILLING_VERIFIED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertCertificate(jdbc, "AAAAAAAAAAAAAAAAAAAAAAAAAA", 1L, 1L, "VALID", requestId, "BILLING_VERIFIED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void onlyOneValidCertificatePerEnrollmentButExpiredOnesAreKept() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("190")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);
            long requestId = insertRequest(jdbc, 1L, null);

            insertCertificate(jdbc, "AAAAAAAAAAAAAAAAAAAAAAAAAA", 1L, null, "EXPIRED", requestId, "BILLING_VERIFIED", null);
            insertCertificate(jdbc, "BBBBBBBBBBBBBBBBBBBBBBBBBB", 1L, null, "EXPIRED", requestId, "BILLING_VERIFIED", null);
            insertCertificate(jdbc, "CCCCCCCCCCCCCCCCCCCCCCCCCC", 1L, null, "VALID", requestId, "BILLING_VERIFIED", null);
            insertCertificate(jdbc, "DDDDDDDDDDDDDDDDDDDDDDDDDD", 2L, null, "VALID", requestId, "BILLING_VERIFIED", null);

            assertThatThrownBy(() -> insertCertificate(jdbc, "EEEEEEEEEEEEEEEEEEEEEEEEEE", 1L, null, "VALID", requestId, "BILLING_VERIFIED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertCertificate(jdbc, "CCCCCCCCCCCCCCCCCCCCCCCCCC", 3L, null, "VALID", requestId, "BILLING_VERIFIED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void manuallyConfirmedStandingRequiresWhoWhenAndEvidence() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("190")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);
            long requestId = insertRequest(jdbc, 1L, null);

            assertThatThrownBy(() -> insertCertificate(jdbc, "AAAAAAAAAAAAAAAAAAAAAAAAAA", 1L, null, "VALID", requestId, "MANUALLY_CONFIRMED", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            insertCertificate(jdbc, "BBBBBBBBBBBBBBBBBBBBBBBBBB", 1L, null, "VALID", requestId, "MANUALLY_CONFIRMED", 9L);
            assertThat(jdbc.queryForObject("select count(*) from regular_student_certificate", Integer.class)).isEqualTo(1);
        }
    }
}
