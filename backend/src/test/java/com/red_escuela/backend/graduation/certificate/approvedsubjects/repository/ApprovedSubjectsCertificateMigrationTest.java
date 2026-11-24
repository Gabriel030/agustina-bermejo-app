package com.red_escuela.backend.graduation.certificate.approvedsubjects.repository;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class ApprovedSubjectsCertificateMigrationTest {

    private static final String INSERT = "insert into approved_subjects_certificate (code, program_enrollment_id, student_id, status, "
            + "issued_at, expires_at, validity_days, issued_by_id, recipient_type, recipient_text, snapshot, template_ref, "
            + "render_engine_version, pdf_sha256) values (?, 1, 1, ?, now(), now() + interval '30 days', 30, 1, ?, ?, '{}'::jsonb, "
            + "'APPROVED_SUBJECTS_BUILT_IN_V1', '1', ?)";
    private static final String SHA = "a".repeat(64);

    private JdbcTemplate migratedJdbc(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table approved_subjects_certificate disable trigger all");
        return jdbc;
    }

    private void insert(JdbcTemplate jdbc, String code, String status, String recipientType, String recipientText) {
        jdbc.update(INSERT, code, status, recipientType, recipientText, SHA);
    }

    @Test
    void validRowWithAndWithoutRecipientIsAccepted() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("465")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            insert(jdbc, "CODE-A", "VALID", null, null);
            insert(jdbc, "CODE-B", "VALID", "EMPLOYER", "Acme SA");

            assertThat(jdbc.queryForObject("select count(*) from approved_subjects_certificate", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void statusAndRecipientTypeMustBeKnownValues() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("465")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insert(jdbc, "CODE-A", "BOGUS", null, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert(jdbc, "CODE-B", "VALID", "BOGUS", "x")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void recipientTypeAndTextMustComeTogether() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("465")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insert(jdbc, "CODE-A", "VALID", "EMPLOYER", null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert(jdbc, "CODE-B", "VALID", null, "Acme SA")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void recipientTextRejectsLineBreaksAndOverlongValues() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("465")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            assertThatThrownBy(() -> insert(jdbc, "CODE-A", "VALID", "OTHER", "linea1\nlinea2")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insert(jdbc, "CODE-B", "VALID", "OTHER", "x".repeat(81))).isInstanceOf(DataIntegrityViolationException.class);
            insert(jdbc, "CODE-C", "VALID", "OTHER", "x".repeat(80));
        }
    }

    @Test
    void codeIsUnique() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("465")) {
            JdbcTemplate jdbc = migratedJdbc(dataSource);

            insert(jdbc, "CODE-A", "VALID", null, null);

            assertThatThrownBy(() -> insert(jdbc, "CODE-A", "VALID", null, null)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
