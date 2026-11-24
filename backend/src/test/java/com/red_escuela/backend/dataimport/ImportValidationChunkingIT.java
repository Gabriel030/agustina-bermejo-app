package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportFileRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.dataimport.specialist.ImportBatchValidator;
import com.red_escuela.backend.dataimport.specialist.ImportDefinitionRegistry;
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
            where r.batch_id = ? and r.entity_type = 'SECTION_ENROLLMENTS'
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
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZC", year, firstDni, 6, 4);
        String finalDate = year + "-07-20";
        String enrollments = HistoricalDatasetCsv.SECTION_ENROLLMENT_HEADER
                + row(csv, 0, 0, 1, "8,50", finalDate)
                + row(csv, 1, 0, 1, "11", finalDate)
                + row(csv, 2, 0, 1, "8,50", finalDate)
                + row(csv, 0, 0, 1, "8,50", finalDate)
                + row(csv, 3, 1, 1, "8,50", finalDate)
                + row(csv, 3, 1, 1, "8,50", finalDate)
                + row(csv, 4, 2, 1, "8,50", year + "-02-01")
                + row(csv, 2, 0, 1, "8,50", finalDate)
                + row(csv, 5, 3, 1, "8,50", finalDate);

        long structureBatch = createBatch();
        long singleChunkBatch = createBatch();
        long smallChunksBatch = createBatch();
        try {
            importBaseStructure(structureBatch, csv);
            upload(singleChunkBatch, "SECTION_ENROLLMENTS", enrollments);
            upload(smallChunksBatch, "SECTION_ENROLLMENTS", enrollments);

            validate(singleChunkBatch);
            ImportBatchValidator smallChunksValidator = new ImportBatchValidator(batchRepository, fileRepository, rowRepository,
                    eventRepository, registry, entityManager, 2);
            transactions.executeWithoutResult(status -> smallChunksValidator.validate(smallChunksBatch, directorId));

            List<String> expected = jdbc.queryForList(ROWS_WITH_ERRORS_IN_STORED_ORDER, String.class, singleChunkBatch);
            List<String> actual = jdbc.queryForList(ROWS_WITH_ERRORS_IN_STORED_ORDER, String.class, smallChunksBatch);
            assertThat(expected).hasSize(9).anyMatch(line -> line.contains("DUPLICATED_IN_FILE")).anyMatch(line -> line.contains("OUT_OF_RANGE"));
            assertThat(actual).containsExactlyElementsOf(expected);
        } finally {
            rollback(structureBatch);
        }
    }

    private String row(HistoricalDatasetCsv csv, int student, int section, int attempt, String score, String finalDate) {
        return "DNI," + csv.studentDni(student) + "," + csv.program() + "," + csv.sectionCode(section) + "," + attempt
                + ",APPROVED,\"" + score + "\",PROMOTION," + finalDate + "\n";
    }
}
