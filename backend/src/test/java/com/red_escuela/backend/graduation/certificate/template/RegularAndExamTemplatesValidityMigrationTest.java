package com.red_escuela.backend.graduation.certificate.template;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class RegularAndExamTemplatesValidityMigrationTest {

    private static final String SEEDED_TEXT = "Esta constancia tiene validez de 30 días desde su fecha de emisión.";
    private static final String NEW_TEXT = "Esta constancia es válida hasta el {validUntil}.";

    @Test
    void publishesVersionTwoWithTheValidityMarkerForRegularAndExamCertificatesKeepingVersionOneUntouched() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("472")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            List<Map<String, Object>> rows = versionsOf(jdbc, "REGULAR_STUDENT_CERTIFICATE", "EXAM_CERTIFICATE");

            assertThat(rows).hasSize(4);
            assertThat(rows).filteredOn(row -> row.get("version_number").equals(1))
                    .allSatisfy(row -> assertThat(row.get("approval_text")).isEqualTo(SEEDED_TEXT));
            assertThat(rows).filteredOn(row -> row.get("version_number").equals(2))
                    .hasSize(2)
                    .allSatisfy(row -> {
                        assertThat(row.get("approval_text")).isEqualTo(NEW_TEXT);
                        assertThat(row.get("status")).isEqualTo("PUBLISHED");
                    });
        }
    }

    @Test
    void leavesApprovedSubjectsAndExamRegistrationTemplatesWithOnlyVersionOne() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("472")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThat(versionsOf(jdbc, "APPROVED_SUBJECTS_CERTIFICATE", "EXAM_REGISTRATION_CERTIFICATE"))
                    .hasSize(2)
                    .allSatisfy(row -> assertThat(row.get("version_number")).isEqualTo(1));
        }
    }

    @Test
    void doesNotPublishOverATemplateWhoseCurrentVersionWasAlreadyEdited() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("471")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into certificate_template_version (template_id, version_number, status, approval_text, title, updated_at, published_at, "
                    + "published_by_name) select t.id, 2, 'PUBLISHED', 'Texto editado por la dirección', 'CONSTANCIA DE ALUMNO REGULAR', now(), now(), 'Dirección' "
                    + "from certificate_template t where t.document_type = 'REGULAR_STUDENT_CERTIFICATE'");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("472").load().migrate();

            assertThat(versionsOf(jdbc, "REGULAR_STUDENT_CERTIFICATE")).extracting(row -> row.get("version_number")).containsExactly(1, 2);
            assertThat(versionsOf(jdbc, "EXAM_CERTIFICATE")).extracting(row -> row.get("version_number")).containsExactly(1, 2);
        }
    }

    private List<Map<String, Object>> versionsOf(JdbcTemplate jdbc, String... documentTypes) {
        String inList = String.join(",", java.util.Collections.nCopies(documentTypes.length, "?"));
        return jdbc.queryForList("select t.document_type, v.version_number, v.status, v.approval_text from certificate_template_version v "
                + "join certificate_template t on t.id = v.template_id where t.document_type in (" + inList + ") "
                + "order by t.document_type, v.version_number", (Object[]) documentTypes);
    }
}
