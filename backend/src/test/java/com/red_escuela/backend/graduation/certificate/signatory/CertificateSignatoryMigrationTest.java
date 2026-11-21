package com.red_escuela.backend.graduation.certificate.signatory;

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
class CertificateSignatoryMigrationTest {

    private void migrate(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    private void seedDraftWithCustomSigners(JdbcTemplate jdbc) {
        jdbc.update("insert into certificate_template_image (template_id, purpose, content_type, size_bytes, width_pixels, height_pixels, sha256, content, uploaded_at) "
                + "select id, 'SIGNATURE', 'image/png', 3, 1, 1, repeat('b', 64), ?, now() from certificate_template where kind = 'PROGRAM'", new byte[]{1, 2, 3});
        jdbc.update("insert into certificate_template_version (template_id, status, title, updated_at, first_signer_name, first_signer_role, "
                + "first_signer_signature_image_id, second_signer_name, second_signer_role) "
                + "select t.id, 'DRAFT', 'Borrador', now(), 'Ana Gómez', 'Rectora', (select max(id) from certificate_template_image), 'Ana Gómez', 'Rectora' "
                + "from certificate_template t where t.kind = 'PROGRAM'");
    }

    @Test
    void seededSignersBecomeCentralSignatoriesWithGraduationApprovalPermission() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("274")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource);

            List<Map<String, Object>> signatories = jdbc.queryForList("select name, role, active from certificate_signatory order by role");

            assertThat(signatories).extracting(row -> row.get("role")).containsExactly("Director Académico", "Secretaria Legal");
            assertThat(signatories).allSatisfy(row -> {
                assertThat(row.get("name")).isEqualTo("Dirección");
                assertThat(row.get("active")).isEqualTo(true);
            });
            assertThat(jdbc.queryForList("select distinct document_type from certificate_signatory_permission", String.class))
                    .containsExactly("GRADUATION_APPROVAL");
            assertThat(jdbc.queryForObject("select count(*) from certificate_signatory_permission", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void publishedVersionsAreLeftUntouchedAndDraftsAreLinked() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("274")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedDraftWithCustomSigners(jdbc);
            migrate(dataSource);

            assertThat(jdbc.queryForObject("select count(*) from certificate_template_version where status = 'PUBLISHED' "
                    + "and (signatory1_id is not null or signatory2_id is not null)", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from certificate_template_version where status = 'PUBLISHED' "
                    + "and first_signer_role = 'Secretaria Legal' and second_signer_role = 'Director Académico'", Integer.class)).isEqualTo(2);
            Map<String, Object> draft = jdbc.queryForMap("select signatory1_id, signatory2_id from certificate_template_version where status = 'DRAFT'");
            assertThat(draft.get("signatory1_id")).isNotNull();
            assertThat(draft.get("signatory2_id")).isNull();
            assertThat(jdbc.queryForObject("select s.role from certificate_signatory s join certificate_template_version v on v.signatory1_id = s.id "
                    + "where v.status = 'DRAFT'", String.class)).isEqualTo("Rectora");
        }
    }

    @Test
    void signatureImageOfTheSignerIsCopiedToTheCentralSignatory() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("274")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedDraftWithCustomSigners(jdbc);
            migrate(dataSource);

            Map<String, Object> signature = jdbc.queryForMap("select s.role, g.sha256, g.size_bytes from certificate_signatory_signature g "
                    + "join certificate_signatory s on s.id = g.signatory_id");

            assertThat(signature.get("role")).isEqualTo("Rectora");
            assertThat(signature.get("sha256")).isEqualTo("b".repeat(64));
            assertThat(jdbc.queryForObject("select count(*) from certificate_signatory_signature", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void constraintsRejectInvalidPermissionsDuplicatedSignatoriesAndOversizedSignatures() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("274")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource);

            assertThatThrownBy(() -> jdbc.update("insert into certificate_signatory_permission (signatory_id, document_type) "
                    + "select id, 'OTHER' from certificate_signatory limit 1")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_signatory (name, role, active, created_at, updated_at) "
                    + "values (' ', 'Cargo', true, now(), now())")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template_version (template_id, status, updated_at, signatory1_id, signatory2_id) "
                    + "select t.id, 'DRAFT', now(), s.id, s.id from certificate_template t, certificate_signatory s where t.kind = 'COURSE' limit 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_signatory_signature (signatory_id, content_type, size_bytes, width_pixels, "
                    + "height_pixels, sha256, content, uploaded_at) select id, 'image/png', 99, 1, 1, repeat('a', 64), ?, now() "
                    + "from certificate_signatory limit 1", new byte[]{1})).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void auditConstraintsAcceptSignatoryActionsAndKeepExistingOnes() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("274")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource);
            String insert = "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), ?, ?, 1)";

            for (String action : List.of("CERTIFICATE_SIGNATORY_CREATED", "CERTIFICATE_SIGNATORY_UPDATED", "CERTIFICATE_SIGNATORY_PERMS_CHANGED",
                    "CERTIFICATE_SIGNATORY_DEACTIVATED", "CERTIFICATE_SIGNATORY_ACTIVATED", "CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED",
                    "CERTIFICATE_SIGNATORY_SIGNATURE_DELETED")) {
                jdbc.update(insert, action, "CERTIFICATE_SIGNATORY");
            }
            assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_SIGNATORY'", Integer.class)).isEqualTo(7);
            jdbc.update(insert, "STAFF_WITHDRAWN", "PERSON");
            assertThatThrownBy(() -> jdbc.update(insert, "CERTIFICATE_SIGNATORY_UNKNOWN", "CERTIFICATE_SIGNATORY"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
