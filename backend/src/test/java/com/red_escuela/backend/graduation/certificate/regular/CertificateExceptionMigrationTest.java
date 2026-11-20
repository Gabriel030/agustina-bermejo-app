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
class CertificateExceptionMigrationTest {

    private static final String REASON = "Reclamo formal ante el organismo de defensa";
    private static final String INSERT = "insert into certificate_exception_approval (student_id, kind, program_enrollment_id, course_enrollment_id, "
            + "organism_kind, organism_reference, reason, status, requested_by_id, requested_at, decided_by_id, decided_at, decision_reason, "
            + "valid_until, consumed_at, consumed_certificate_id) values (1, 'PROGRAM', ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, ?, ?, null, null)";

    private JdbcTemplate migratedJdbc(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table certificate_exception_approval disable trigger all");
        jdbc.execute("alter table certificate_exception_status_change disable trigger all");
        jdbc.execute("alter table regular_student_certificate disable trigger all");
        return jdbc;
    }

    private void insertException(JdbcTemplate jdbc, Long programEnrollmentId, Long courseEnrollmentId, String organismKind, String reference,
                                 String reason, String status, long requestedBy, Long decidedBy) {
        boolean decided = decidedBy != null;
        jdbc.update(INSERT, programEnrollmentId, courseEnrollmentId, organismKind, reference, reason, status, requestedBy, decidedBy,
                decided ? java.sql.Timestamp.valueOf("2026-11-10 12:00:00") : null, decided ? "ok verificado" : null,
                decided ? java.sql.Timestamp.valueOf("2026-11-17 12:00:00") : null);
    }

    @Test
    void decidedByMustDifferFromRequestedBy() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("271")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "APPROVED", 10L, 10L))
                    .isInstanceOf(DataIntegrityViolationException.class);
            insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "APPROVED", 10L, 20L);
            insertException(jdbc, 1L, null, "COURT", "EXP-2", REASON, "PENDING", 10L, null);
            assertThat(jdbc.queryForObject("select count(*) from certificate_exception_approval", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void reasonReferenceKindAndEnrollmentAreEnforced() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("271")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "EXP-1", "demasiado corto", "PENDING", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "   ", REASON, "PENDING", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "UNKNOWN", "EXP-1", REASON, "PENDING", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "BOGUS", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, null, null, "COURT", "EXP-1", REASON, "PENDING", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, 1L, 1L, "COURT", "EXP-1", REASON, "PENDING", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void approvedOrConsumedWithoutDecisionOrConsumptionDataIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("271")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "APPROVED", 10L, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "CONSUMED", 10L, 20L))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void historyRowsNeedExactlyOneActor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("271")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);
            insertException(jdbc, 1L, null, "COURT", "EXP-1", REASON, "PENDING", 10L, null);
            long id = jdbc.queryForObject("select max(id) from certificate_exception_approval", Long.class);
            String sql = "insert into certificate_exception_status_change (approval_id, from_status, to_status, event, actor_person_id, actor_process, "
                    + "changed_at) values (?, null, 'PENDING', 'REQUEST', ?, ?, now())";

            assertThatThrownBy(() -> jdbc.update(sql, id, null, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(sql, id, 10L, "job")).isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(sql, id, 10L, null);
            jdbc.update(sql, id, null, "job");
        }
    }
}
