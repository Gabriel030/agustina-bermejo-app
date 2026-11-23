package com.red_escuela.backend.schema;

import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
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

@SlowTest(reason = "Migrates a real database to the latest version to insert every signatory document type through its CHECK constraint")
@Tag("migration")
class SignatoryPermissionConstraintTest {

    @Test
    void everySignatoryDocumentTypeIsAcceptedByTheMigratedCheck() {
        List<String> rejected = new ArrayList<>();
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long signatoryId = jdbc.queryForObject(
                    "insert into certificate_signatory (name, role, active, created_at, updated_at) values ('Firmante', 'Rol', true, now(), now()) returning id",
                    Long.class);
            for (SignatoryDocumentType type : SignatoryDocumentType.values()) {
                try {
                    jdbc.update("insert into certificate_signatory_permission (signatory_id, document_type) values (?, ?)", signatoryId, type.name());
                } catch (RuntimeException e) {
                    rejected.add(type.name());
                }
            }
        }
        assertThat(rejected).isEmpty();
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
