package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates real databases to run the release scripts against the schema they are meant for")
@Tag("migration")
class ReleaseScriptsMigrationTest {

    private static final Path PRE_MIGRATION_CHECKS = Path.of("..", "docs", "release", "pre-migration-checks.sql");
    private static final Path LEGACY_SAMPLE_DATA = Path.of("..", "docs", "release", "legacy-sample-data.sql");
    private static final Pattern BLOCK_HEADER = Pattern.compile("^select :current_version < (\\d+) as run_block \\\\gset$");
    private static final int MINIMUM_VERSIONED_BLOCKS = 21;
    private static final String SAMPLE_DATA_BASE_VERSION = "445";

    @Test
    void preMigrationChecksRunAgainstTheSchemaEachBlockTargets() {
        Map<Integer, List<String>> blocks = versionedBlocksOf(readLines(PRE_MIGRATION_CHECKS));
        assertThat(blocks).hasSizeGreaterThanOrEqualTo(MINIMUM_VERSIONED_BLOCKS);
        List<String> failures = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> block : blocks.entrySet()) {
            String version = String.valueOf(latestVersionBefore(block.getKey()));
            try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo(version)) {
                runReadOnly(database, block.getValue(), "V" + block.getKey(), failures);
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void preMigrationChecksAlwaysRunBlocksRunAgainstTheBaseAndTheLatestSchema() {
        List<String> alwaysRun = alwaysRunStatementsOf(readLines(PRE_MIGRATION_CHECKS));
        assertThat(alwaysRun).isNotEmpty();
        List<String> failures = new ArrayList<>();
        for (String version : List.of(SAMPLE_DATA_BASE_VERSION, latestMigrationVersion())) {
            try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo(version)) {
                runReadOnly(database, alwaysRun, "always-run@V" + version, failures);
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void legacySampleDataLoadsIntoTheLatestSchema() throws SQLException {
        List<String> statements = statementsOf(readLines(LEGACY_SAMPLE_DATA)).stream()
                .filter(statement -> !statement.equalsIgnoreCase("begin") && !statement.equalsIgnoreCase("commit"))
                .toList();
        assertThat(statements).isNotEmpty();
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo(latestMigrationVersion());
             Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException e) {
                        throw new AssertionError("legacy-sample-data.sql statement failed: " + sql + " -> " + e.getMessage(), e);
                    }
                }
                try (ResultSet rows = statement.executeQuery("select count(*) from person where id between 9001 and 9999")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isPositive();
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static void runReadOnly(DataSource dataSource, List<String> statements, String label, List<String> failures) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(true);
            for (String sql : statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                } catch (SQLException e) {
                    failures.add("[" + label + "] " + e.getMessage() + " <- " + sql.lines().findFirst().orElse(""));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<Integer, List<String>> versionedBlocksOf(List<String> lines) {
        Map<Integer, List<String>> blocks = new TreeMap<>(Comparator.naturalOrder());
        Integer threshold = null;
        List<String> body = new ArrayList<>();
        for (String line : lines) {
            Matcher header = BLOCK_HEADER.matcher(line.trim());
            if (header.matches()) {
                threshold = Integer.parseInt(header.group(1));
                body = new ArrayList<>();
            } else if (threshold != null && line.trim().equals("\\endif")) {
                blocks.computeIfAbsent(threshold, key -> new ArrayList<>()).addAll(statementsOf(body));
                threshold = null;
            } else if (threshold != null) {
                body.add(line);
            }
        }
        return blocks;
    }

    private static List<String> alwaysRunStatementsOf(List<String> lines) {
        List<String> outside = new ArrayList<>();
        boolean inBlock = false;
        for (String line : lines) {
            if (BLOCK_HEADER.matcher(line.trim()).matches()) {
                inBlock = true;
            } else if (inBlock && line.trim().equals("\\endif")) {
                inBlock = false;
            } else if (!inBlock && !line.contains(":current_version") && !line.contains("\\gset")) {
                outside.add(line);
            }
        }
        return statementsOf(outside);
    }

    static List<String> statementsOf(List<String> lines) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideDollarQuote = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (current.isEmpty() && (trimmed.isEmpty() || trimmed.startsWith("\\") || trimmed.startsWith("--"))) {
                continue;
            }
            current.append(line).append('\n');
            if (Pattern.compile("\\$\\$").matcher(line).results().count() % 2 == 1) {
                insideDollarQuote = !insideDollarQuote;
            }
            if (!insideDollarQuote && trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        assertThat(current.toString().trim()).isEmpty();
        return statements;
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String latestMigrationVersion() {
        return String.valueOf(migrationVersions().stream().max(Comparator.naturalOrder()).orElseThrow());
    }

    private static int latestVersionBefore(int threshold) {
        return migrationVersions().stream().filter(version -> version < threshold).max(Comparator.naturalOrder()).orElseThrow();
    }

    private static List<Integer> migrationVersions() {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql"))
                    .map(resource -> Integer.parseInt(resource.getFilename().substring(1, resource.getFilename().indexOf("__"))))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
