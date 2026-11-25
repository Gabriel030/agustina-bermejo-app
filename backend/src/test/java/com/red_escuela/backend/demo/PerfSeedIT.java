package com.red_escuela.backend.demo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PerfSeedIT extends AbstractFullDemoSeedIT {

    @Test
    void largeSeedLoadsAgainstCurrentSchemaAndIsRerunnable() throws Exception {
        runSeedScript();
        Map<String, Long> firstLoad = loadedVolumes();

        runSeedScript();

        assertThat(firstLoad).containsEntry("students", 5000L)
                .containsEntry("instructors", 50L)
                .containsEntry("sections", 300L)
                .containsEntry("enrollments", 40000L)
                .containsEntry("invoices", 30000L)
                .containsEntry("notifications", 50000L)
                .containsEntry("auditLog", 100000L);
        assertThat(firstLoad.get("attendance")).isPositive();
        assertThat(firstLoad.get("gradeItems")).isPositive();
        assertThat(loadedVolumes()).isEqualTo(firstLoad);
    }

    private Map<String, Long> loadedVolumes() {
        return Map.ofEntries(
                Map.entry("students", count("select count(*) from student where id between 20001 and 25000")),
                Map.entry("instructors", count("select count(*) from instructor where id between 30001 and 30050")),
                Map.entry("sections", count("select count(*) from subject_section where id between 14001 and 14300")),
                Map.entry("enrollments", count("select count(*) from subject_section_enrollment where id >= 100000")),
                Map.entry("attendance", count("select count(*) from attendance where id >= 1000000")),
                Map.entry("gradeItems", count("select count(*) from grade_item where id >= 3000000")),
                Map.entry("invoices", count("select count(*) from invoice where id >= 500000")),
                Map.entry("notifications", count("select count(*) from notification where message like 'Notificación perf %'")),
                Map.entry("auditLog", count("select count(*) from audit_log where reason like 'perf %'")));
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private void runSeedScript() throws Exception {
        Path seed = Path.of("").toAbsolutePath().getParent().resolve("perf/seed-large.sql");
        String sql = Files.readAllLines(seed, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.startsWith("\\") && !line.startsWith("create extension"))
                .collect(Collectors.joining("\n"));
        jdbcTemplate.update("insert into institution (id, name) values (1, 'Perf institution') on conflict (id) do nothing");
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            try {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("rollback");
                }
            }
        }
    }
}
