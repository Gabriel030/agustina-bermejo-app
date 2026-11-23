package com.red_escuela.backend.schema;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the latest version to insert every audit action and entity type through its CHECK constraints")
@Tag("migration")
class AuditEnumConstraintsTest {

    @Test
    void everyAuditActionAndEntityTypeIsAcceptedByTheMigratedChecks() {
        List<String> rejected = new ArrayList<>();
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (AuditAction action : AuditAction.values()) {
                if (!accepted(jdbc, action.name(), AuditEntityType.PERSON.name())) {
                    rejected.add("action " + action);
                }
            }
            for (AuditEntityType entityType : AuditEntityType.values()) {
                if (!accepted(jdbc, AuditAction.STAFF_WITHDRAWN.name(), entityType.name())) {
                    rejected.add("entityType " + entityType);
                }
            }
        }
        assertThat(rejected).isEmpty();
    }

    private static boolean accepted(JdbcTemplate jdbc, String action, String entityType) {
        try {
            jdbc.update("insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), ?, ?, 1)", action, entityType);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String latestMigrationVersion() {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql"))
                    .map(resource -> resource.getFilename().substring(1, resource.getFilename().indexOf("__")))
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
