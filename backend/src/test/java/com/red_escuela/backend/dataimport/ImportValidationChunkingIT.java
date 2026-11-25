package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportFileRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.dataimport.specialist.ImportBatchValidator;
import com.red_escuela.backend.dataimport.specialist.ImportDefinitionRegistry;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportValidationChunkingIT extends AbstractDataImportIT {
    private static final String ROWS_WITH_ERRORS_IN_STORED_ORDER = """
            select r.row_number || ' ' || r.status || ' [' || coalesce((
                select string_agg(e.value ->> 'column' || ':' || (e.value ->> 'code'), ',' order by e.position)
                from jsonb_array_elements(case when jsonb_typeof(r.errors) = 'array' then r.errors else '[]'::jsonb end)
                     with ordinality as e(value, position)), '') || ']'
            from import_row r
            where r.batch_id = ? and r.entity_type = ?
            order by r.row_number
            """;

    @Autowired
    private ImportBatchRepository batchRepository;
    @Autowired
    private ImportFileRepository fileRepository;
    @Autowired
    private ImportRowRepository rowRepository;
    @Autowired
    private ImportBatchEventRepository eventRepository;
    @Autowired
    private ImportDefinitionRegistry registry;

    @Test
    void batchLargerThanChunkReportsTheSameStatusesAndErrorsAsSingleChunk() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZK");
        String finalDate = year + "-07-20";
        assertSameResultForChunkSize("SECTION_ENROLLMENTS", csv, enrollments(csv, finalDate, true), 2, true);
    }

    @Test
    void batchWithRowCountMultipleOfChunkReportsTheSameResultAsSingleChunk() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZL");
        String finalDate = year + "-07-20";
        assertSameResultForChunkSize("SECTION_ENROLLMENTS", csv, enrollments(csv, finalDate, false), 4, true);
    }

    @Test
    void prerequisiteCycleAcrossChunksReportsTheSameResultAsSingleChunk() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZM");
        String prerequisites = HistoricalDatasetCsv.PREREQUISITE_HEADER
                + prerequisite(csv, 1, 0) + prerequisite(csv, 2, 1) + prerequisite(csv, 3, 2) + prerequisite(csv, 0, 1);
        assertSameResultForChunkSize("PREREQUISITES", csv, prerequisites, 2, false);
    }

    @Test
    void chunkSizeIsCappedAtTheInClauseLimit() {
        assertThat(validatorWithChunkSize(Integer.MAX_VALUE).validationChunkSize()).isEqualTo(InClauseChunks.DEFAULT_SIZE);
        assertThat(validatorWithChunkSize(0).validationChunkSize()).isEqualTo(1);
    }

    private HistoricalDatasetCsv newDataset(String subjectPrefix) {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        return new HistoricalDatasetCsv(suffix, subjectPrefix, year, firstDni, 6, 4);
    }

    private String enrollments(HistoricalDatasetCsv csv, String finalDate, boolean withLastRow) {
        return HistoricalDatasetCsv.SECTION_ENROLLMENT_HEADER
                + row(csv, 0, 0, 1, "8,50", finalDate)
                + row(csv, 1, 0, 1, "11", finalDate)
                + row(csv, 2, 0, 1, "8,50", finalDate)
                + row(csv, 0, 0, 1, "8,50", finalDate)
                + row(csv, 3, 1, 1, "8,50", finalDate)
                + row(csv, 3, 1, 1, "8,50", finalDate)
                + row(csv, 4, 2, 1, "8,50", year + "-02-01")
                + row(csv, 2, 0, 1, "8,50", finalDate)
                + (withLastRow ? row(csv, 5, 3, 1, "8,50", finalDate) : "");
    }

    private String prerequisite(HistoricalDatasetCsv csv, int subject, int required) {
        return csv.program() + "," + csv.subjectCode(subject) + "," + csv.subjectCode(required) + ",APPROVED,4\n";
    }

    private ImportBatchValidator validatorWithChunkSize(int chunkSize) {
        return new ImportBatchValidator(batchRepository, fileRepository, rowRepository, eventRepository, registry, entityManager,
                chunkSize);
    }

    private void assertSameResultForChunkSize(String entityType, HistoricalDatasetCsv csv, String content, int chunkSize,
            boolean expectDuplicatesAndRange) throws Exception {
        long structureBatch = createBatch();
        long singleChunkBatch = createBatch();
        long smallChunksBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv);
            upload(singleChunkBatch, entityType, content);
            upload(smallChunksBatch, entityType, content);

            validate(singleChunkBatch);
            ImportBatchValidator smallChunksValidator = validatorWithChunkSize(chunkSize);
            transactions.executeWithoutResult(status -> smallChunksValidator.validate(smallChunksBatch, directorId));

            List<String> expected = jdbc.queryForList(ROWS_WITH_ERRORS_IN_STORED_ORDER, String.class, singleChunkBatch, entityType);
            List<String> actual = jdbc.queryForList(ROWS_WITH_ERRORS_IN_STORED_ORDER, String.class, smallChunksBatch, entityType);
            if (expectDuplicatesAndRange) {
                assertThat(expected).anyMatch(line -> line.contains("DUPLICATED_IN_FILE")).anyMatch(line -> line.contains("OUT_OF_RANGE"));
            } else {
                assertThat(expected).anyMatch(line -> line.contains("PREREQUISITE_CYCLE"));
            }
            assertThat(actual).isNotEmpty().containsExactlyElementsOf(expected);
        } finally {
            rollback(structureBatch);
        }
    }

    private String row(HistoricalDatasetCsv csv, int student, int section, int attempt, String score, String finalDate) {
        return "DNI," + csv.studentDni(student) + "," + csv.program() + "," + csv.sectionCode(section) + "," + attempt
                + ",APPROVED,\"" + score + "\",PROMOTION," + finalDate + "\n";
    }
}
