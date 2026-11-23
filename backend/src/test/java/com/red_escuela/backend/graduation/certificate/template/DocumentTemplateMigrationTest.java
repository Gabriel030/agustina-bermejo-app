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
class DocumentTemplateMigrationTest {

    @Test
    void seedsOnePublishedVersionOnePerDocumentTypeAndKeepsEgresoTemplates() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("462")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            List<Map<String, Object>> rows = jdbc.queryForList("select t.document_type, v.version_number, v.status, v.title "
                    + "from certificate_template t join certificate_template_version v on v.template_id = t.id "
                    + "where t.document_type is not null order by t.document_type");

            assertThat(rows).extracting(row -> row.get("document_type")).containsExactly("APPROVED_SUBJECTS_CERTIFICATE",
                    "EXAM_CERTIFICATE", "EXAM_REGISTRATION_CERTIFICATE", "REGULAR_STUDENT_CERTIFICATE");
            assertThat(rows).extracting(row -> row.get("title")).containsExactly("CONSTANCIA DE MATERIAS APROBADAS",
                    "CONSTANCIA DE EXAMEN RENDIDO", "CONSTANCIA DE INSCRIPCIÓN A EXAMEN", "CONSTANCIA DE ALUMNO REGULAR");
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("version_number")).isEqualTo(1);
                assertThat(row.get("status")).isEqualTo("PUBLISHED");
            });
            assertThat(jdbc.queryForObject("select count(*) from certificate_template where kind is not null", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void rejectsUnknownDocumentTypeRowsWithBothOrNoDiscriminatorAndDuplicates() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("462")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThatThrownBy(() -> jdbc.update("insert into certificate_template (document_type) values ('OTHER')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template (kind, document_type) values ('COURSE', 'EXAM_CERTIFICATE')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template (kind) values (null)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into certificate_template (document_type) values ('EXAM_CERTIFICATE')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
