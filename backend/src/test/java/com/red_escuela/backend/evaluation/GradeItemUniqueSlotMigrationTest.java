package com.red_escuela.backend.evaluation;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class GradeItemUniqueSlotMigrationTest {

    private void insertGrade(JdbcTemplate jdbc, String column, long enrollmentId, String type, Integer sequence, double score) {
        jdbc.update("insert into grade_item (" + column + ", type, sequence_number, score) values (?, ?, ?, ?)",
                enrollmentId, type, sequence, score);
    }

    @Test
    void duplicatedGradeSlotsKeepOnlyTheMostRecentRowAndNewDuplicatesAreRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("170")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table grade_item disable trigger all");
            insertGrade(jdbc, "program_section_enrollment_id", 1, "EXAM", 1, 5.0);
            insertGrade(jdbc, "program_section_enrollment_id", 1, "EXAM", 1, 8.0);
            insertGrade(jdbc, "program_section_enrollment_id", 1, "EXAM", 2, 6.0);
            insertGrade(jdbc, "program_section_enrollment_id", 2, "EXAM", 1, 7.0);
            insertGrade(jdbc, "program_section_enrollment_id", 3, "FINAL", null, 4.0);
            insertGrade(jdbc, "program_section_enrollment_id", 3, "FINAL", null, 9.0);
            insertGrade(jdbc, "course_section_enrollment_id", 1, "EXAM", 1, 3.0);
            insertGrade(jdbc, "course_section_enrollment_id", 1, "EXAM", 1, 10.0);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForList("select score from grade_item order by id", Double.class))
                    .containsExactly(8.0, 6.0, 7.0, 9.0, 10.0);
            assertThatThrownBy(() -> insertGrade(jdbc, "program_section_enrollment_id", 1, "EXAM", 1, 1.0))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertGrade(jdbc, "program_section_enrollment_id", 3, "FINAL", null, 1.0))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertGrade(jdbc, "course_section_enrollment_id", 1, "EXAM", 1, 1.0))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
