package com.red_escuela.backend.graduation.certificate.template;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class CertificateTemplateMigrationTest {

    private static final String INSERT_DRAFT = "insert into certificate_template_version (template_id, status, title, updated_at) "
            + "select id, 'DRAFT', 'Borrador', now() from certificate_template where kind = ?";

    @Test
    void seedsPublishedVersionOnePerKindWithDesignAText() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            List<Map<String, Object>> rows = jdbc.queryForList("select t.kind, v.version_number, v.status, v.title, v.intro_text, "
                    + "v.approval_text, v.institute_name, v.footer_legend, v.first_signer_role, v.second_signer_role, v.published_by_name "
                    + "from certificate_template t join certificate_template_version v on v.template_id = t.id order by t.kind");

            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(row -> row.get("kind")).containsExactly("COURSE", "PROGRAM");
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("version_number")).isEqualTo(1);
                assertThat(row.get("status")).isEqualTo("PUBLISHED");
                assertThat(row.get("title")).isEqualTo("CERTIFICADO DE APROBACIÓN");
                assertThat(row.get("intro_text")).isEqualTo("Se certifica que");
                assertThat(row.get("institute_name")).isEqualTo("INSTITUTO AGUSTINA BERMEJO");
                assertThat(row.get("footer_legend")).isEqualTo(
                        "Certificado emitido por el Instituto Agustina Bermejo. Verificá su autenticidad escaneando el código QR.");
                assertThat(row.get("first_signer_role")).isEqualTo("Secretaria Legal");
                assertThat(row.get("second_signer_role")).isEqualTo("Director Académico");
            });
            assertThat(rows.get(0).get("approval_text")).isEqualTo("ha aprobado la Capacitación en");
            assertThat(rows.get(1).get("approval_text")).isEqualTo("ha aprobado la Tecnicatura en");
        }
    }

    @Test
    void publishedVersionCannotBeUpdatedOrDeletedAtDatabaseLevel() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThatThrownBy(() -> jdbc.update("update certificate_template_version set title = 'Alterado' where version_number = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("immutable");
            assertThatThrownBy(() -> jdbc.update("update certificate_template_version set status = 'DRAFT', version_number = null, "
                    + "published_at = null, published_by_name = null where version_number = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("delete from certificate_template_version where version_number = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select count(*) from certificate_template_version where title = 'CERTIFICADO DE APROBACIÓN'",
                    Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void draftIsEditableDeletableAndPublishableButThenFrozen() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update(INSERT_DRAFT, "PROGRAM");

            jdbc.update("update certificate_template_version set title = 'Editado' where status = 'DRAFT'");
            jdbc.update("update certificate_template_version set status = 'PUBLISHED', version_number = 2, published_at = now(), "
                    + "published_by_name = 'Alguien' where status = 'DRAFT'");

            assertThatThrownBy(() -> jdbc.update("update certificate_template_version set title = 'Otra vez' where version_number = 2"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(INSERT_DRAFT, "PROGRAM");
            jdbc.update("delete from certificate_template_version where status = 'DRAFT'");
            assertThat(jdbc.queryForObject("select count(*) from certificate_template_version", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    void onlyOneDraftPerTemplateAndUniqueVersionNumbers() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update(INSERT_DRAFT, "PROGRAM");

            assertThatThrownBy(() -> jdbc.update(INSERT_DRAFT, "PROGRAM")).isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(INSERT_DRAFT, "COURSE");
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template_version (template_id, version_number, status, updated_at, "
                    + "published_at, published_by_name) select id, 1, 'PUBLISHED', now(), now(), 'X' from certificate_template where kind = 'PROGRAM'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void draftAndPublishedStatesAreConsistentAndEnumsAreChecked() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThatThrownBy(() -> jdbc.update("insert into certificate_template_version (template_id, version_number, status, updated_at) "
                    + "select id, 5, 'DRAFT', now() from certificate_template where kind = 'PROGRAM'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template_version (template_id, status, updated_at) "
                    + "select id, 'ARCHIVED', now() from certificate_template where kind = 'PROGRAM'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template (kind) values ('OTHER')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void imagesAreImmutablePngWithinTwoMegabytes() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            String insert = "insert into certificate_template_image (template_id, purpose, content_type, size_bytes, width_pixels, "
                    + "height_pixels, sha256, content, uploaded_at) select id, ?, ?, 3, 1, 1, repeat('a', 64), ?, now() "
                    + "from certificate_template where kind = 'PROGRAM'";
            byte[] bytes = {1, 2, 3};

            jdbc.update(insert, "LOGO", "image/png", bytes);

            assertThatThrownBy(() -> jdbc.update(insert, "BANNER", "image/png", bytes)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, "LOGO", "image/jpeg", bytes)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update certificate_template_image set purpose = 'SIGNATURE'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("delete from certificate_template_image"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void versionAndImageTablesCannotBeTruncated() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("273")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThatThrownBy(() -> jdbc.execute("truncate certificate_template_version cascade"))
                    .hasMessageContaining("immutable");
            assertThatThrownBy(() -> jdbc.execute("truncate certificate_template_image cascade"))
                    .hasMessageContaining("immutable");
            assertThat(jdbc.queryForObject("select count(*) from certificate_template_version", Integer.class)).isEqualTo(2);
        }
    }
}
