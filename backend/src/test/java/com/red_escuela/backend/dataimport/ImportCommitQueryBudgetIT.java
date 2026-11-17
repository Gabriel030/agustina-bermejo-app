package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportCommitQueryBudgetIT extends AbstractDataImportIT {
    private static final int STUDENTS = 100;
    private static final int SECTIONS = 20;
    private static final int ROWS = STUDENTS * SECTIONS;
    private static final double MAX_STATEMENTS_PER_ROW = 5.0;
    private static final Pattern EXECUTION = Pattern.compile("duration: [0-9.]+ ms\\s+execute [^:]*: (.*)");
    private static final Pattern TRANSACTION_CONTROL = Pattern.compile("(?i)^\\s*(begin|commit|rollback|set |show |discard ).*");

    @BeforeEach
    void enableStatementLogging() {
        jdbc.execute("alter system set log_min_duration_statement = 0");
        jdbc.execute("select pg_reload_conf()");
        await().until(() -> "0".equals(jdbc.queryForObject("show log_min_duration_statement", String.class)));
    }

    @AfterEach
    void disableStatementLogging() {
        jdbc.execute("alter system reset log_min_duration_statement");
        jdbc.execute("select pg_reload_conf()");
    }

    @Test
    void committingTwoThousandSectionEnrollmentsStaysWithinFiveStatementsPerRow() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZB", year, firstDni, STUDENTS, SECTIONS);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        long batchId = createBatch();
        upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, STUDENTS));

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));

        String startMarker = "budget-start-" + UUID.randomUUID();
        String endMarker = "budget-end-" + UUID.randomUUID();
        jdbc.queryForObject("select '" + startMarker + "'", String.class);
        long startedAt = System.nanoTime();
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));
        long elapsedNanos = System.nanoTime() - startedAt;
        jdbc.queryForObject("select '" + endMarker + "'", String.class);

        String logs = await().until(postgres::getLogs, text -> text.contains(endMarker));
        String window = logs.substring(logs.lastIndexOf(startMarker), logs.lastIndexOf(endMarker));
        long statements = window.lines().map(EXECUTION::matcher).filter(java.util.regex.Matcher::find)
                .map(matcher -> matcher.group(1)).filter(sql -> !TRANSACTION_CONTROL.matcher(sql).matches()).count();
        double perRow = (double) statements / ROWS;
        System.out.printf("QUERY BUDGET rows=%d statements=%d perRow=%.3f rowsPerSecond=%.0f%n", ROWS, statements, perRow,
                ROWS / (elapsedNanos / 1_000_000_000.0));

        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'COMMITTED'", Integer.class,
                batchId)).isEqualTo(ROWS);
        assertThat(perRow).isLessThanOrEqualTo(MAX_STATEMENTS_PER_ROW);

        rollback(batchId);
        rollback(baseBatch);
    }
}
