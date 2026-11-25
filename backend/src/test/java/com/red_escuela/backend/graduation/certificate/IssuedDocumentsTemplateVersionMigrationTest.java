package com.red_escuela.backend.graduation.certificate;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class IssuedDocumentsTemplateVersionMigrationTest {

    private static final String SHA = "a".repeat(64);

    private static final Map<String, String> INSERTS = Map.of(
            "regular_student_certificate",
            "insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, expires_at, validity_days, issued_by_id, "
                    + "issue_channel, request_id, billing_mode_at_issue, standing_basis, snapshot, template_ref, pdf_sha256, pdf_path) "
                    + "values ('00000000000000000000000000', 'PROGRAM', 1, 1, 'VALID', now(), now() + interval '30 days', 30, 1, 'STUDENT_PORTAL', 1, 'OFF', "
                    + "'BILLING_VERIFIED', '{}'::jsonb, 'REGULAR_BUILT_IN_V1', '" + SHA + "', 'p.pdf')",
            "exam_registration_certificate",
            "insert into exam_registration_certificate (code, student_id, registration_id, exam_board_id, status, issued_at, expires_at, "
                    + "issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) "
                    + "values ('CODE-EXAM-REG', 1, 1, 1, 'VALID', now(), now() + interval '30 days', 1, '{}'::jsonb, "
                    + "'EXAM_REGISTRATION_BUILT_IN_V1', '" + SHA + "', 'p.pdf')",
            "exam_certificate",
            "insert into exam_certificate (code, registration_id, exam_board_id, student_id, status, origin, issued_at, expires_at, "
                    + "validity_days, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) "
                    + "values ('CODE-EXAM', 1, 1, 1, 'VALID', 'SELF_SERVICE', now(), now() + interval '30 days', 30, 1, '{}'::jsonb, "
                    + "'EXAM_BUILT_IN_V1', '" + SHA + "', 'p.pdf')",
            "approved_subjects_certificate",
            "insert into approved_subjects_certificate (code, program_enrollment_id, student_id, status, issued_at, expires_at, "
                    + "validity_days, issued_by_id, snapshot, template_ref, render_engine_version, pdf_sha256) "
                    + "values ('CODE-APPROVED', 1, 1, 'VALID', now(), now() + interval '30 days', 30, 1, '{}'::jsonb, "
                    + "'APPROVED_SUBJECTS_BUILT_IN_V1', '1', '" + SHA + "')");

    private static final List<String> TABLES = List.copyOf(INSERTS.keySet());

    private JdbcTemplate migrated(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private void insertRowBypassingParentConstraints(JdbcTemplate jdbc, String table) {
        jdbc.execute("alter table " + table + " disable trigger all");
        jdbc.execute(INSERTS.get(table));
        jdbc.execute("alter table " + table + " enable trigger all");
    }

    private long insertTemplateVersion(JdbcTemplate jdbc) {
        Long templateId = jdbc.queryForObject("select id from certificate_template where document_type = 'REGULAR_STUDENT_CERTIFICATE'", Long.class);
        return jdbc.queryForObject("insert into certificate_template_version (template_id, version_number, status, updated_at, published_at, published_by_name) "
                + "values (?, 999, 'PUBLISHED', now(), now(), 'Test') returning id", Long.class, templateId);
    }

    @Test
    void everyIssuedDocumentTableHasNullableTemplateVersionColumn() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("471")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            for (String table : TABLES) {
                List<Map<String, Object>> columns = jdbc.queryForList(
                        "select data_type, is_nullable from information_schema.columns "
                                + "where table_name = ? and column_name = 'template_version_id'", table);
                assertThat(columns).as(table).hasSize(1);
                assertThat(columns.get(0)).as(table).containsEntry("data_type", "bigint").containsEntry("is_nullable", "YES");
            }
        }
    }

    @Test
    void everyTemplateVersionColumnReferencesCertificateTemplateVersion() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("471")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            for (String table : TABLES) {
                Integer foreignKeys = jdbc.queryForObject(
                        "select count(*) from pg_constraint c "
                                + "join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey) "
                                + "where c.contype = 'f' and c.conrelid = ?::regclass and a.attname = 'template_version_id' "
                                + "and c.confrelid = 'certificate_template_version'::regclass", Integer.class, table);
                assertThat(foreignKeys).as(table).isEqualTo(1);
            }
        }
    }

    @Test
    void existingRowsStayValidWithNullTemplateVersion() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (String table : TABLES) {
                insertRowBypassingParentConstraints(jdbc, table);
            }

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            for (String table : TABLES) {
                assertThat(jdbc.queryForObject("select count(*) from " + table + " where template_version_id is null", Integer.class))
                        .as(table).isEqualTo(1);
            }
        }
    }

    @Test
    void existingTemplateVersionCanBeReferencedAndMissingOneIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("471")) {
            JdbcTemplate jdbc = migrated(dataSource);
            long versionId = insertTemplateVersion(jdbc);

            for (String table : TABLES) {
                insertRowBypassingParentConstraints(jdbc, table);

                assertThat(jdbc.update("update " + table + " set template_version_id = ?", versionId)).as(table).isEqualTo(1);
                assertThatThrownBy(() -> jdbc.update("update " + table + " set template_version_id = ?", versionId + 1_000_000))
                        .as(table).isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }
}
