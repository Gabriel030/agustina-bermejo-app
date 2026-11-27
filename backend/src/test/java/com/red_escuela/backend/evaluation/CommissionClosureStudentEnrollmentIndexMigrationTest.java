package com.red_escuela.backend.evaluation;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class CommissionClosureStudentEnrollmentIndexMigrationTest {

    @Test
    void migrationCreatesTheEnrollmentIndexOnlyFromV473() {
        try (MigrationTestDatabase before = MigrationTestDatabase.migratedTo("472")) {
            assertThat(indexDefinition(new JdbcTemplate(before))).isNull();
        }
        try (MigrationTestDatabase after = MigrationTestDatabase.migratedTo("473")) {
            assertThat(indexDefinition(new JdbcTemplate(after)))
                    .contains(" ON public.commission_closure_student USING btree (enrollment_id)");
        }
    }

    private String indexDefinition(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select indexdef from pg_indexes where schemaname = current_schema() and indexname = 'idx_commission_closure_student_enrollment'",
                String.class).stream().findFirst().orElse(null);
    }
}
