package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the latest version to compare its CHECK constraints with the persisted enums")
@Tag("migration")
class EnumCheckConstraintsTest {

    private static final Pattern LITERAL = Pattern.compile("'([^']*)'::");

    private static final Pattern SCRIPT_ENTRY = Pattern.compile("\\('(\\w+)', '(\\w+)', array\\[([^\\]]*)\\]\\)");

    private static final String ENUM_CHECKS = """
            select t.relname, a.attname, pg_get_constraintdef(c.oid)
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            join pg_namespace n on n.oid = t.relnamespace
            join pg_attribute a on a.attrelid = t.oid and a.attnum = c.conkey[1]
            where c.contype = 'c' and n.nspname = current_schema() and c.conname = t.relname || '_' || a.attname || '_enum'
            """;

    private static final String CHECKS_ON_COLUMN = """
            select pg_get_constraintdef(c.oid)
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            join pg_namespace n on n.oid = t.relnamespace
            join pg_attribute a on a.attrelid = t.oid and a.attnum = c.conkey[1]
            where c.contype = 'c' and array_length(c.conkey, 1) = 1
              and n.nspname = current_schema() and t.relname = ? and a.attname = ?
            """;

    private static final Map<String, Set<String>> INTENTIONAL_DIFFERENCES = Map.of(
            "discount_definition.applicable_invoice_type", Set.of("ADMINISTRATIVE_FEE", "ANNUAL_UPFRONT", "DEGREE_ISSUANCE_FEE", "ENROLLMENT_FEE", "EXAM_FEE", "MONTHLY_INSTALLMENT", "RETAKEN_SUBJECT_FEE"),
            "student_discount.applicable_invoice_type", Set.of("ADMINISTRATIVE_FEE", "ANNUAL_UPFRONT", "DEGREE_ISSUANCE_FEE", "ENROLLMENT_FEE", "EXAM_FEE", "MONTHLY_INSTALLMENT", "RETAKEN_SUBJECT_FEE"),
            "discount_definition.discount_type", Set.of("ANNUAL_UPFRONT", "COHORT_PROMOTION", "PAYMENT_METHOD", "SCHOLARSHIP"),
            "student_discount.discount_type", Set.of("ANNUAL_UPFRONT", "COHORT_ENTRY", "COHORT_PROMOTION", "INDIVIDUAL", "SCHOLARSHIP"),
            "invoice_discount.discount_type", Set.of("ANNUAL_UPFRONT", "COHORT_ENTRY", "COHORT_PROMOTION", "INDIVIDUAL", "SCHOLARSHIP"),
            "invoice.issuing_reason", Set.of("AD_HOC_CHARGE", "ANNUAL_UPFRONT_PAYMENT", "CATCH_UP", "COURSE_ADMISSION", "COURSE_MONTHLY_RUN", "ENROLLMENT_FEE_ADVANCE",
                    "ENROLLMENT_FEE_ON_DEMAND", "LATE_FEE", "LEGACY", "MONTHLY_RUN", "PAYMENT_PLAN_SELECTION", "PROGRAM_ADMISSION", "REACTIVATION", "UPFRONT", "YEAR_CONFIGURATION_CHANGE"),
            "monthly_invoice_run_item_attempt.result", Set.of("ALREADY_ISSUED", "FAILED", "ISSUED", "NOT_APPLICABLE"),
            "monthly_invoice_simulation_line.result", Set.of("ALREADY_ISSUED", "FAILED", "ISSUED", "NOT_APPLICABLE"));

    @Test
    void everyPersistedEnumColumnHasACheckListingExactlyTheJavaConstants() {
        List<String> violations = new ArrayList<>();
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (PersistedEnumColumns.EnumColumn column : PersistedEnumColumns.discover()) {
                List<String> definitions = jdbc.queryForList(CHECKS_ON_COLUMN, String.class, column.table(), column.column());
                Set<String> allowed = new TreeSet<>();
                definitions.forEach(definition -> allowed.addAll(literalsOf(definition)));
                Set<String> expected = INTENTIONAL_DIFFERENCES.getOrDefault(column.table() + "." + column.column(), column.values());
                if (!allowed.equals(expected)) {
                    violations.add(column.table() + "." + column.column() + " (" + column.enumType().getSimpleName() + ") check=" + allowed + " enum=" + column.values());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void preMigrationCheckReportsValuesOutsideTheEnum() throws Exception {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion());
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("alter table holiday drop constraint holiday_type_enum");
            statement.execute("insert into holiday (date, name, type) values (date '2099-01-01', 'Bogus', 'BOGUS')");
            statement.execute("insert into holiday (date, name, type) values (date '2099-01-02', 'Valid', 'NATIONAL')");
            statement.execute(preMigrationCheckBlock());
            List<String> notices = new ArrayList<>();
            for (SQLWarning warning = statement.getWarnings(); warning != null; warning = warning.getNextWarning()) {
                notices.add(warning.getMessage());
            }
            assertThat(notices).hasSize(1);
            assertThat(notices.get(0)).contains("holiday.type").contains("BOGUS").contains("1 filas");
        }
    }

    @Test
    void preMigrationCheckListsMatchTheChecksAddedByTheMigrations() throws Exception {
        Map<String, Set<String>> fromScript = new TreeMap<>();
        Matcher entries = SCRIPT_ENTRY.matcher(preMigrationCheckBlock());
        while (entries.find()) {
            fromScript.put(entries.group(1) + "." + entries.group(2), literalsInArray(entries.group(3)));
        }
        Map<String, Set<String>> fromDatabase = new TreeMap<>();
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            new JdbcTemplate(dataSource).query(ENUM_CHECKS, rs -> {
                fromDatabase.put(rs.getString(1) + "." + rs.getString(2), literalsOf(rs.getString(3)));
            });
        }
        assertThat(fromScript).isNotEmpty().isEqualTo(fromDatabase);
    }

    private static Set<String> literalsInArray(String array) {
        Set<String> literals = new TreeSet<>();
        Matcher matcher = Pattern.compile("'([^']*)'").matcher(array);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        return literals;
    }

    private static String preMigrationCheckBlock() throws IOException {
        String script = Files.readString(Path.of("..", "docs", "release", "pre-migration-checks.sql"));
        Matcher block = Pattern.compile("(?s)\\bdo \\$\\$.*?\\bend \\$\\$;").matcher(script);
        assertThat(block.find()).isTrue();
        return block.group();
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

    static Set<String> literalsOf(String definition) {
        Set<String> literals = new TreeSet<>();
        Matcher matcher = LITERAL.matcher(definition);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        return literals;
    }
}
