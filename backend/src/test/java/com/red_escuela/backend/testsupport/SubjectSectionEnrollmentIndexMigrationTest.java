package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class SubjectSectionEnrollmentIndexMigrationTest {

    private static final String INDEX_DEFINITION_QUERY = "select indexdef from pg_indexes where schemaname = current_schema() "
            + "and indexname = 'ix_subject_section_enrollment_section_status'";

    @Test
    void migrationCreatesValidCompositeIndex() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("360")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThat(jdbc.queryForObject(INDEX_DEFINITION_QUERY, String.class))
                    .contains(" ON public.subject_section_enrollment USING btree (program_section_id, status)");
            assertThat(jdbc.queryForObject(
                    "select i.indisvalid from pg_index i join pg_class c on c.oid = i.indexrelid where c.relname = 'ix_subject_section_enrollment_section_status'",
                    Boolean.class)).isTrue();
        }
    }

    @Test
    void migrationStatementIsIdempotentWhenIndexAlreadyExists() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("360")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            jdbc.execute("create index if not exists ix_subject_section_enrollment_section_status "
                    + "on subject_section_enrollment (program_section_id, status)");

            assertThat(jdbc.queryForList(INDEX_DEFINITION_QUERY, String.class)).hasSize(1);
        }
    }
}
