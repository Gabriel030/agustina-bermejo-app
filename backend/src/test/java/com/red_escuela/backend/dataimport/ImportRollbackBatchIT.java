package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportRollbackBatchIT extends AbstractDataImportIT {
    private static final int FEW = 10;
    private static final int MANY = 100;
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
    void rollbackRestoresEnrollmentsAndClosureResultsToTheirStateBeforeTheImport() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZR", 2011, FEW);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        long enrollmentsBefore = count("subject_section_enrollment");
        long closureResultsBefore = count("commission_closure_student");
        long closuresBefore = count("commission_closure");
        long batchId = createBatch();
        upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, FEW));
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));
        assertThat(count("subject_section_enrollment")).isEqualTo(enrollmentsBefore + FEW);
        assertThat(count("commission_closure_student")).isEqualTo(closureResultsBefore + FEW);

        rollback(batchId).andExpect(status().isOk());

        assertThat(count("subject_section_enrollment")).isEqualTo(enrollmentsBefore);
        assertThat(count("commission_closure_student")).isEqualTo(closureResultsBefore);
        assertThat(count("commission_closure")).isEqualTo(closuresBefore);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Integer.class, batchId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'VALID'", Integer.class,
                batchId)).isEqualTo(FEW);
        rollback(baseBatch);
    }

    @Test
    void rollbackStatementCountDoesNotGrowWithTheNumberOfEnrollments() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZS", 2012, MANY);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        long fewStatements = statementsToRollBack(csv, FEW);
        long manyStatements = statementsToRollBack(csv, MANY);
        rollback(baseBatch);
        System.out.printf("ROLLBACK QUERIES enrollments=%d statements=%d | enrollments=%d statements=%d%n", FEW, fewStatements,
                MANY, manyStatements);
        assertThat(manyStatements).isEqualTo(fewStatements);
    }

    private long statementsToRollBack(HistoricalDatasetCsv csv, int students) throws Exception {
        long batchId = createBatch();
        upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, students));
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));

        String startMarker = "rollback-start-" + UUID.randomUUID();
        String endMarker = "rollback-end-" + UUID.randomUUID();
        jdbc.queryForObject("select '" + startMarker + "'", String.class);
        rollback(batchId).andExpect(status().isOk());
        jdbc.queryForObject("select '" + endMarker + "'", String.class);

        String logs = await().until(postgres::getLogs, text -> text.contains(endMarker));
        String window = logs.substring(logs.lastIndexOf(startMarker), logs.lastIndexOf(endMarker));
        long statements = window.lines().map(EXECUTION::matcher).filter(Matcher::find).map(matcher -> matcher.group(1))
                .filter(sql -> !TRANSACTION_CONTROL.matcher(sql).matches()).count();
        return statements;
    }

    private HistoricalDatasetCsv newDataset(String subjectPrefix, int datasetYear, int students) {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        return new HistoricalDatasetCsv(suffix, subjectPrefix, datasetYear, firstDni, students, 1);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
