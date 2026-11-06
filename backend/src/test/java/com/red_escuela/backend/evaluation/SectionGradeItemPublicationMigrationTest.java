package com.red_escuela.backend.evaluation;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class SectionGradeItemPublicationMigrationTest {

    private void insertGrade(JdbcTemplate jdbc, String column, long enrollmentId, String type, Integer sequence) {
        jdbc.update("insert into grade_item (" + column + ", type, sequence_number, score) values (?, ?, ?, 7.0)",
                enrollmentId, type, sequence);
    }

    private void insertEnrollment(JdbcTemplate jdbc, String table, String sectionColumn, long id, long sectionId) {
        jdbc.update("insert into " + table + " (id, " + sectionColumn + ", student_id, "
                        + (table.startsWith("subject") ? "program_enrollment_id" : "course_enrollment_id") + ") "
                        + "overriding system value values (?, ?, 1, 1)", id, sectionId);
    }

    @Test
    void existingGradesBecomePublishedPerSectionAndItemExceptFinals() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("187")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table grade_item disable trigger all");
            jdbc.execute("alter table subject_section_enrollment disable trigger all");
            jdbc.execute("alter table course_section_enrollment disable trigger all");
            insertEnrollment(jdbc, "subject_section_enrollment", "program_section_id", 1, 10);
            insertEnrollment(jdbc, "subject_section_enrollment", "program_section_id", 2, 10);
            insertEnrollment(jdbc, "subject_section_enrollment", "program_section_id", 3, 11);
            insertEnrollment(jdbc, "course_section_enrollment", "course_section_id", 1, 20);
            insertGrade(jdbc, "program_section_enrollment_id", 1, "EXAM", 1);
            insertGrade(jdbc, "program_section_enrollment_id", 2, "EXAM", 1);
            insertGrade(jdbc, "program_section_enrollment_id", 2, "TP", 1);
            insertGrade(jdbc, "program_section_enrollment_id", 3, "EXAM", 1);
            insertGrade(jdbc, "program_section_enrollment_id", 3, "FINAL", null);
            insertGrade(jdbc, "course_section_enrollment_id", 1, "EXAM", 2);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select section_kind, section_id, grade_item_type, sequence_number from section_grade_item_publication "
                            + "order by section_kind, section_id, grade_item_type, sequence_number");
            assertThat(rows).extracting(row -> row.get("section_kind") + ":" + row.get("section_id") + ":"
                            + row.get("grade_item_type") + ":" + row.get("sequence_number"))
                    .containsExactly("COURSE:20:EXAM:2", "PROGRAM:10:EXAM:1", "PROGRAM:10:TP:1", "PROGRAM:11:EXAM:1");
            assertThat(jdbc.queryForObject(
                    "select count(*) from section_grade_item_publication where published_at is null", Integer.class)).isZero();
        }
    }
}
