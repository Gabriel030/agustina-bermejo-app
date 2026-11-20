package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.shared.exception.HistoricalDiscardBlockedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportRollbackBatchIT extends AbstractDataImportIT {
    private static final int FEW = 10;
    private static final int MANY = 100;
    private static final int BEYOND_ONE_CHUNK = 1005;
    private static final int BLOCKING_POSITION = 57;
    private static final long STATEMENTS_TO_ROLL_BACK = 14;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HistoricalPersonRecorder personRecorder;

    @Test
    void rollbackRestoresEnrollmentsAndClosureResultsToTheirStateBeforeTheImport() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZR", 2011, FEW);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        long enrollmentsBefore = count("subject_section_enrollment");
        long closureResultsBefore = count("commission_closure_student");
        long closuresBefore = count("commission_closure");
        long batchId = importEnrollments(csv, FEW);
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
    void rollbackOfOneImportKeepsExactlyTheEnrollmentsAndResultsOfTheOtherImport() throws Exception {
        HistoricalDatasetCsv first = newDataset("ZA", 2011, FEW);
        HistoricalDatasetCsv second = newDataset("ZB", 2012, FEW);
        long firstBase = createBatch();
        importBaseStructure(firstBase, first);
        long secondBase = createBatch();
        importBaseStructure(secondBase, second);
        long firstBatch = importEnrollments(first, FEW);
        long secondBatch = importEnrollments(second, FEW);
        List<Long> firstIds = importedEnrollmentIds(firstBatch);
        List<Long> secondIds = importedEnrollmentIds(secondBatch);
        assertThat(firstIds).hasSize(FEW);
        assertThat(secondIds).hasSize(FEW);
        assertThat(existingEnrollmentIds(firstIds)).containsExactlyInAnyOrderElementsOf(firstIds);
        assertThat(existingResultEnrollmentIds(firstIds)).containsExactlyInAnyOrderElementsOf(firstIds);

        rollback(firstBatch).andExpect(status().isOk());

        assertThat(existingEnrollmentIds(firstIds)).isEmpty();
        assertThat(existingResultEnrollmentIds(firstIds)).isEmpty();
        assertThat(existingEnrollmentIds(secondIds)).containsExactlyInAnyOrderElementsOf(secondIds);
        assertThat(existingResultEnrollmentIds(secondIds)).containsExactlyInAnyOrderElementsOf(secondIds);
        assertThat(importedEnrollmentIds(secondBatch)).containsExactlyInAnyOrderElementsOf(secondIds);
        assertThat(importedEnrollmentIds(firstBatch)).isEmpty();
        rollback(secondBatch).andExpect(status().isOk());
        rollback(secondBase);
        rollback(firstBase);
    }

    @Test
    void rollbackDeletesImportedEnrollmentsThatWereModifiedAfterTheImportAndKeepsTheOtherImportUntouched() throws Exception {
        HistoricalDatasetCsv first = newDataset("ZC", 2011, FEW);
        HistoricalDatasetCsv second = newDataset("ZD", 2012, FEW);
        long firstBase = createBatch();
        importBaseStructure(firstBase, first);
        long secondBase = createBatch();
        importBaseStructure(secondBase, second);
        long firstBatch = importEnrollments(first, FEW);
        long secondBatch = importEnrollments(second, FEW);
        List<Long> firstIds = importedEnrollmentIds(firstBatch);
        List<Long> secondIds = importedEnrollmentIds(secondBatch);
        jdbc.update("update subject_section_enrollment set final_score = 4.50, version = version + 3 where id = any(?)",
                (Object) firstIds.toArray(Long[]::new));
        jdbc.update("update subject_section_enrollment set final_score = 3.25, version = version + 2 where id = ?", secondIds.get(0));

        rollback(firstBatch).andExpect(status().isOk());

        assertThat(existingEnrollmentIds(firstIds)).isEmpty();
        assertThat(existingResultEnrollmentIds(firstIds)).isEmpty();
        assertThat(existingEnrollmentIds(secondIds)).containsExactlyInAnyOrderElementsOf(secondIds);
        assertThat(jdbc.queryForObject("select final_score from subject_section_enrollment where id = ?", java.math.BigDecimal.class,
                secondIds.get(0))).isEqualByComparingTo("3.25");
        rollback(secondBatch).andExpect(status().isOk());
        rollback(secondBase);
        rollback(firstBase);
    }

    @Test
    void rollbackOfMoreEnrollmentsThanOneChunkRemovesAllOfThemAndKeepsTheOtherImport() throws Exception {
        HistoricalDatasetCsv large = newDataset("ZE", 2011, BEYOND_ONE_CHUNK);
        HistoricalDatasetCsv small = newDataset("ZF", 2012, FEW);
        long largeBase = createBatch();
        importBaseStructure(largeBase, large);
        long smallBase = createBatch();
        importBaseStructure(smallBase, small);
        long largeBatch = importEnrollments(large, BEYOND_ONE_CHUNK);
        long smallBatch = importEnrollments(small, FEW);
        List<Long> largeIds = importedEnrollmentIds(largeBatch);
        List<Long> smallIds = importedEnrollmentIds(smallBatch);
        assertThat(largeIds).hasSize(BEYOND_ONE_CHUNK);

        rollback(largeBatch).andExpect(status().isOk());

        assertThat(existingEnrollmentIds(largeIds)).isEmpty();
        assertThat(existingResultEnrollmentIds(largeIds)).isEmpty();
        assertThat(existingEnrollmentIds(smallIds)).containsExactlyInAnyOrderElementsOf(smallIds);
        assertThat(existingResultEnrollmentIds(smallIds)).containsExactlyInAnyOrderElementsOf(smallIds);
        rollback(smallBatch).andExpect(status().isOk());
        rollback(smallBase);
        rollback(largeBase);
    }

    @Test
    void rollbackBlockedByLaterDataOnTheFiftySeventhEnrollmentReportsThatEnrollmentAndChangesNothing() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZG", 2011, MANY);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        long batchId = importEnrollments(csv, MANY);
        List<Long> ids = importedEnrollmentIds(batchId);
        assertThat(ids).hasSize(MANY);
        long blockingId = ids.get(BLOCKING_POSITION - 1);
        String blockingKey = jdbc.queryForObject(
                "select natural_key from import_created_entity where batch_id = ? and entity_type = 'SECTION_ENROLLMENTS' and entity_id = ?",
                String.class, batchId, blockingId);
        jdbc.update("insert into grade_item (program_section_enrollment_id, type, sequence_number, score) values (?, 'EXAM', 1, 7.0)",
                blockingId);
        long enrollmentsAfterImport = count("subject_section_enrollment");
        long resultsAfterImport = count("commission_closure_student");

        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", "Bearer " + directorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRollback").value(false))
                .andExpect(jsonPath("$.blockers[0].entityType").value("SECTION_ENROLLMENTS"))
                .andExpect(jsonPath("$.blockers[0].naturalKey").value(blockingKey));
        rollback(batchId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ROLLBACK_BLOCKED"))
                .andExpect(jsonPath("$.details.blockers[0].entityType").value("SECTION_ENROLLMENTS"))
                .andExpect(jsonPath("$.details.blockers[0].naturalKey").value(blockingKey));

        assertThat(count("subject_section_enrollment")).isEqualTo(enrollmentsAfterImport);
        assertThat(count("commission_closure_student")).isEqualTo(resultsAfterImport);
        assertThat(existingEnrollmentIds(ids)).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("COMMITTED");
        jdbc.update("delete from grade_item where program_section_enrollment_id = ?", blockingId);
        rollback(batchId).andExpect(status().isOk());
        assertThat(existingEnrollmentIds(ids)).isEmpty();
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
        assertThat(manyStatements).isEqualTo(fewStatements);
        assertThat(manyStatements).isEqualTo(STATEMENTS_TO_ROLL_BACK);
    }

    @Test
    void rollbackOfMoreThanOneChunkOfPeopleAndProgramEnrollmentsRestoresEveryPersonTable() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZH", 2011, BEYOND_ONE_CHUNK);
        Map<String, Long> before = personTableCounts();
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        Map<String, Long> afterImport = personTableCounts();
        assertThat(afterImport.get("person")).isGreaterThanOrEqualTo(before.get("person") + BEYOND_ONE_CHUNK);
        assertThat(afterImport.get("program_enrollment")).isEqualTo(before.get("program_enrollment") + BEYOND_ONE_CHUNK);

        rollback(baseBatch).andExpect(status().isOk());

        assertThat(personTableCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Integer.class, baseBatch))
                .isEqualTo(retainedEntityCount(baseBatch));
    }

    @Test
    void rollbackBlockedByAccessGrantedToOneOfManyPeopleReportsThatPersonAndChangesNothing() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZI", 2011, MANY);
        Map<String, Long> before = personTableCounts();
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        Map<String, Long> afterImport = personTableCounts();
        String blockedDni = csv.studentDni(BLOCKING_POSITION - 1);
        jdbc.update("update person set grant_system_access = true where dni = ?", blockedDni);

        rollback(baseBatch).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ROLLBACK_BLOCKED"))
                .andExpect(jsonPath("$.details.blockers[0].entityType").value("PERSONS"));

        assertThat(personTableCounts()).isEqualTo(afterImport);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, baseBatch)).isEqualTo("COMMITTED");
        jdbc.update("update person set grant_system_access = false where dni = ?", blockedDni);
        rollback(baseBatch).andExpect(status().isOk());
        assertThat(personTableCounts()).isEqualTo(before);
    }

    @Test
    void rollbackBlockedByLaterDataOnAPersonInTheSecondChunkFallsBackAndReportsThatPerson() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZJ", 2011, BEYOND_ONE_CHUNK);
        Map<String, Long> before = personTableCounts();
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        Map<String, Long> afterImport = personTableCounts();
        String blockedDni = csv.studentDni(2);
        String blockedKey = jdbc.queryForObject(
                "select c.natural_key from import_created_entity c join person p on p.id = c.entity_id where c.batch_id = ? and c.entity_type = 'PERSONS' and p.dni = ?",
                String.class, baseBatch, blockedDni);
        Long actorId = jdbc.queryForObject("select created_by_person_id from import_batch where id = ?", Long.class, baseBatch);
        jdbc.update("insert into student_status_change (student_id, change_type, reason, actor_person_id, occurred_at) select s.id, 'WITHDRAWN', 'later-chunk', ?, now() from student s join person p on p.id = s.person_id where p.dni = ?",
                actorId, blockedDni);

        rollback(baseBatch).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ROLLBACK_BLOCKED"))
                .andExpect(jsonPath("$.details.blockers[0].entityType").value("PERSONS"))
                .andExpect(jsonPath("$.details.blockers[0].naturalKey").value(blockedKey));

        assertThat(personTableCounts()).isEqualTo(afterImport);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, baseBatch)).isEqualTo("COMMITTED");
        jdbc.update("delete from student_status_change where reason = 'later-chunk'");
        rollback(baseBatch).andExpect(status().isOk());
        assertThat(personTableCounts()).isEqualTo(before);
    }

    @Test
    void personWithAccessInALaterChunkBlocksTheWholeBatchBeforeAnyPersonIsDeleted() throws Exception {
        HistoricalDatasetCsv csv = newDataset("ZK", 2011, BEYOND_ONE_CHUNK);
        long baseBatch = createBatch();
        importBaseStructure(baseBatch, csv);
        List<Long> personIds = jdbc.queryForList(
                "select entity_id from import_created_entity where batch_id = ? and entity_type = 'PERSONS' order by id desc", Long.class,
                baseBatch);
        assertThat(personIds.size()).isGreaterThan(BEYOND_ONE_CHUNK);
        Long accessPersonId = personIds.get(personIds.size() - 3);
        jdbc.update("update person set grant_system_access = true where id = ?", accessPersonId);

        Long remaining = transactions.execute(status -> {
            assertThatThrownBy(() -> personRecorder.discardPersons(personIds)).isInstanceOf(HistoricalDiscardBlockedException.class);
            Long found = ((Number) entityManager.createNativeQuery("select count(*) from person where id in (:ids)")
                    .setParameter("ids", personIds).getSingleResult()).longValue();
            status.setRollbackOnly();
            return found;
        });

        assertThat(remaining).isEqualTo(personIds.size());
        jdbc.update("update person set grant_system_access = false where id = ?", accessPersonId);
        rollback(baseBatch).andExpect(status().isOk());
    }

    private Map<String, Long> personTableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("person", "student", "address", "person_role", "instructor", "program_enrollment")) {
            counts.put(table, count(table));
        }
        return counts;
    }

    private long retainedEntityCount(long batchId) {
        return jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ? and entity_type in ('PROGRAMS', 'SUBJECTS', 'CURRICULUM', 'PREREQUISITES')",
                Long.class, batchId);
    }

    private long statementsToRollBack(HistoricalDatasetCsv csv, int students) throws Exception {
        long batchId = importEnrollments(csv, students);
        String token = directorToken();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/data-imports/" + batchId + "/rollback").header("Authorization", "Bearer " + token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"confirmation\":\"DESHACER\"}"))
                    .andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private long importEnrollments(HistoricalDatasetCsv csv, int students) throws Exception {
        long batchId = createBatch();
        upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, students));
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));
        return batchId;
    }

    private List<Long> importedEnrollmentIds(long batchId) {
        return jdbc.queryForList(
                "select entity_id from import_created_entity where batch_id = ? and entity_type = 'SECTION_ENROLLMENTS' order by id",
                Long.class, batchId);
    }

    private Set<Long> existingEnrollmentIds(List<Long> ids) {
        return Set.copyOf(jdbc.queryForList("select id from subject_section_enrollment where id = any(?)", Long.class,
                (Object) ids.toArray(Long[]::new)));
    }

    private Set<Long> existingResultEnrollmentIds(List<Long> ids) {
        return Set.copyOf(jdbc.queryForList("select enrollment_id from commission_closure_student where enrollment_id = any(?)",
                Long.class, (Object) ids.toArray(Long[]::new)));
    }

    private HistoricalDatasetCsv newDataset(String subjectPrefix, int datasetYear, int students) {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        return new HistoricalDatasetCsv(UUID.randomUUID().toString().substring(0, 8), subjectPrefix, datasetYear, firstDni, students, 1);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
