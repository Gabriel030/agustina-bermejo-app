package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class LegacyPromotionRetirementMigrationTest {

    private static final long BENEFIT_ID = 11L;

    private static void migrateTo(MigrationTestDatabase dataSource, String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private static void insert(JdbcTemplate jdbc, String table, Map<String, Object> values) {
        List<Map<String, Object>> required = jdbc.queryForList("select column_name, data_type from information_schema.columns where table_name = ? and table_schema = 'public' "
                + "and is_nullable = 'NO' and column_default is null and is_identity = 'NO' and is_generated = 'NEVER'", table);
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map<String, Object> column : required) {
            String name = (String) column.get("column_name");
            String type = (String) column.get("data_type");
            row.put(name, switch (type) {
                case "bigint", "integer", "smallint" -> 1;
                case "numeric" -> 1;
                case "boolean" -> false;
                case "date" -> java.sql.Date.valueOf("2027-03-01");
                case "timestamp without time zone", "timestamp with time zone" -> java.sql.Timestamp.valueOf("2027-03-01 10:00:00");
                case "jsonb" -> "{}";
                default -> firstAllowedLiteral(jdbc, table, name);
            });
        }
        row.putAll(values);
        String columns = String.join(", ", row.keySet());
        String marks = row.keySet().stream().map(name -> "value".equals(name) || "student_snapshot".equals(name) ? "?::jsonb" : "?").collect(java.util.stream.Collectors.joining(", "));
        jdbc.update("insert into " + table + " (" + columns + ") values (" + marks + ")", row.values().toArray());
    }

    private static void seedStudentWithPromotionUse(JdbcTemplate jdbc, long student, boolean closedSelection, int uses) {
        long enrollment = 100 + student;
        long selection = 300 + student;
        long account = 500 + student;
        insert(jdbc, "program_enrollment", Map.of("id", enrollment, "student_id", student));
        insert(jdbc, "payment_plan_selection", closedSelection
                ? Map.of("id", selection, "program_enrollment_id", enrollment, "effective_to", java.sql.Date.valueOf("2027-06-01"))
                : Map.of("id", selection, "program_enrollment_id", enrollment));
        jdbc.update("update payment_plan_selection set course_enrollment_id = null where id = ?", selection);
        insert(jdbc, "promotion_redemption", Map.of("id", 400 + student, "promotion_id", 1L, "payment_plan_selection_id", selection));
        insert(jdbc, "student_current_account", Map.of("id", account, "student_id", student, "balance_side", "ZERO", "balance_amount", 0));
        for (int use = 1; use <= uses; use++) {
            long invoice = 600 + student * 10 + use;
            long line = 700 + student * 10 + use;
            insert(jdbc, "invoice", row("id", invoice, "student_current_account_id", account, "document_status", "ISSUED", "invoice_number", "FA-" + invoice,
                    "issue_date", java.sql.Date.valueOf("2027-03-01"), "due_date", java.sql.Date.valueOf("2027-03-11"), "student_snapshot", "{}", "document_type", "INVOICE", "invoice_type", "MONTHLY_INSTALLMENT", "payment_status", "PENDING", "paid_amount", 0, "credited_amount", 0, "discounts_total", 0));
            insert(jdbc, "invoice_line", Map.of("id", line, "invoice_id", invoice, "fee_type", "ADMINISTRATIVE_FEE", "charge_key", "key-" + line, "discount_amount", 0, "credited_amount", 0));
            insert(jdbc, "invoice_discount", Map.of("id", line, "invoice_line_id", line, "student_discount_id", BENEFIT_ID, "discount_type", "PROMOTION"));
        }
    }

    private static String firstAllowedLiteral(JdbcTemplate jdbc, String table, String column) {
        List<String> definitions = jdbc.queryForList("select pg_get_constraintdef(oid) from pg_constraint where conrelid = ?::regclass and contype = 'c'", String.class, table);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(" + column + "\\)::text = ANY \\(\\(?ARRAY\\['([^']+)'");
        return definitions.stream().map(pattern::matcher).filter(java.util.regex.Matcher::find).map(matcher -> matcher.group(1)).findFirst().orElse("ARS");
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return values;
    }

    private static void seedPromotion(JdbcTemplate jdbc) {
        insert(jdbc, "promotion", Map.of("id", 1L, "program_id", 1L));
        insert(jdbc, "promotion_benefit", Map.of("id", BENEFIT_ID, "promotion_id", 1L, "fee_type", "MONTHLY_INSTALLMENT", "invoice_scope_count", 6));
    }

    private interface Scenario {
        void seed(JdbcTemplate jdbc);
    }

    private static void inReplicaSession(MigrationTestDatabase dataSource, Scenario scenario) throws Exception {
        try (Connection connection = dataSource.getConnection(); SingleConnectionDataSource single = new SingleConnectionDataSource(connection, true)) {
            JdbcTemplate jdbc = new JdbcTemplate(single);
            jdbc.execute("set session_replication_role = replica");
            scenario.seed(jdbc);
            jdbc.execute("set session_replication_role = origin");
        }
    }

    @Test
    @Tag("D-074")
    void threeStudentsWithTwoOfSixUsedEachBlockTheMigrationEvenThoughTheGlobalUsesEqualTheScope() throws Exception {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("138")) {
            inReplicaSession(dataSource, jdbc -> {
                seedPromotion(jdbc);
                seedStudentWithPromotionUse(jdbc, 1, false, 2);
                seedStudentWithPromotionUse(jdbc, 2, false, 2);
                seedStudentWithPromotionUse(jdbc, 3, false, 2);
            });

            assertThatThrownBy(() -> migrateTo(dataSource, "139")).hasMessageContaining("3 redemption(s)");
        }
    }

    @Test
    @Tag("D-074")
    void closedSelectionsAndFullyUsedRedemptionsDoNotBlockAndTheLegacyUsesLoseTheirStudentDiscountId() throws Exception {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("138")) {
            inReplicaSession(dataSource, jdbc -> {
                seedPromotion(jdbc);
                seedStudentWithPromotionUse(jdbc, 1, true, 2);
                seedStudentWithPromotionUse(jdbc, 2, false, 6);
            });

            migrateTo(dataSource, "139");

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("select count(*) from invoice_discount where discount_type = 'COHORT_PROMOTION' and student_discount_id is null", Long.class)).isEqualTo(8L);
            assertThat(jdbc.queryForObject("select count(*) from invoice_discount where student_discount_id = ?", Long.class, BENEFIT_ID)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from invoice_discount where discount_type = 'PROMOTION'", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select to_regclass('promotion')", String.class)).isNull();
        }
    }
}
