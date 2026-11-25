package com.red_escuela.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@SlowTest(reason = "Migrates a real database to the version before the import id sequences, loads rows and migrates to the latest version")
@Tag("migration")
class ImportIdSequencesMigrationTest {

    private static final String VERSION_BEFORE_SEQUENCES = "467";
    private static final long HIGHEST_EXISTING_ID = 7_000L;
    private static final long ALLOCATION_SIZE = 50L;

    @Test
    void sequencesStartAboveTheHighestExistingIdAndKeepDirectInsertsWorking() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(VERSION_BEFORE_SEQUENCES)) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table import_row disable trigger all");
            jdbc.execute("alter table import_created_entity disable trigger all");
            insertImportRow(jdbc, 1L);
            insertImportRow(jdbc, HIGHEST_EXISTING_ID);
            insertCreatedEntity(jdbc, 1L);
            insertCreatedEntity(jdbc, HIGHEST_EXISTING_ID);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(incrementOf(jdbc, "import_row_seq")).isEqualTo(ALLOCATION_SIZE);
            assertThat(incrementOf(jdbc, "import_created_entity_seq")).isEqualTo(ALLOCATION_SIZE);

            long firstBlockTopOfRows = jdbc.queryForObject("select nextval('import_row_seq')", Long.class);
            long firstBlockTopOfCreated = jdbc.queryForObject("select nextval('import_created_entity_seq')", Long.class);
            assertThat(firstBlockTopOfRows - ALLOCATION_SIZE).isGreaterThan(HIGHEST_EXISTING_ID);
            assertThat(firstBlockTopOfCreated - ALLOCATION_SIZE).isGreaterThan(HIGHEST_EXISTING_ID);

            long directRowId = insertImportRow(jdbc, null);
            long directCreatedId = insertCreatedEntity(jdbc, null);
            assertThat(directRowId).isGreaterThan(HIGHEST_EXISTING_ID);
            assertThat(directCreatedId).isGreaterThan(HIGHEST_EXISTING_ID);
            assertThat(jdbc.queryForObject("select count(*) from import_row", Long.class)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("select count(*) from import_created_entity", Long.class)).isEqualTo(3L);
        }
    }

    private static long incrementOf(JdbcTemplate jdbc, String sequence) {
        return jdbc.queryForObject("select increment_by from pg_sequences where sequencename = ?", Long.class, sequence);
    }

    private static long insertImportRow(JdbcTemplate jdbc, Long id) {
        if (id == null) {
            return jdbc.queryForObject("""
                    insert into import_row (batch_id, entity_type, row_number, natural_key, content_hash, payload, status)
                    values (1, 'PERSONS', 1, 'k', 'h', '{}'::jsonb, 'PENDING') returning id
                    """, Long.class);
        }
        jdbc.update("""
                insert into import_row (id, batch_id, entity_type, row_number, natural_key, content_hash, payload, status)
                values (?, 1, 'PERSONS', 1, 'k', 'h', '{}'::jsonb, 'PENDING')
                """, id);
        return id;
    }

    private static long insertCreatedEntity(JdbcTemplate jdbc, Long id) {
        if (id == null) {
            return jdbc.queryForObject("""
                    insert into import_created_entity (batch_id, entity_type, entity_id, natural_key, created_at)
                    values (1, 'PERSONS', 1, 'k', now()) returning id
                    """, Long.class);
        }
        jdbc.update("""
                insert into import_created_entity (id, batch_id, entity_type, entity_id, natural_key, created_at)
                values (?, 1, 'PERSONS', 1, 'k', now())
                """, id);
        return id;
    }
}
