package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.specialist.ImportBatchLifecycleRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportDefinitionRegistry;
import com.red_escuela.backend.dataimport.specialist.ImportRollbackTransaction;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DataImportLifecycleIT extends AbstractIntegrationTest {
    private static final String PERSON_HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone\n";
    private static final String PROGRAM_HEADER = "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n";
    private static final List<String> BUSINESS_TABLES = List.of("person", "address", "student", "instructor", "program",
            "price", "price_version", "subject", "program_subject", "program_subject_prerequisite", "academic_offering_cycle",
            "subject_section", "subject_section_instructor", "program_enrollment", "subject_section_enrollment",
            "commission_closure", "commission_closure_student", "term");
    private static final List<String> SIDE_EFFECT_TABLES = List.of("notification", "notification_delivery",
            "event_publication", "invoice", "payment", "current_account_entry");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private ImportDefinitionRegistry registry;
    @Autowired
    private ImportBatchLifecycleRecorder lifecycleRecorder;
    @Autowired
    private ImportRollbackTransaction rollbackTransaction;

    private String directorToken;
    private String suffix;
    private String studentDni;
    private String teacherDni;

    @BeforeEach
    void prepare() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
        cleanImportTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        studentDni = uniqueDni();
        teacherDni = uniqueDni();
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            directorToken = jwtService.generateAccessToken(persistDirector());
        });
    }

    @AfterEach
    void restore() {
        cleanImportTables();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL' where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void rollbackRemovesEverythingTheBatchCreatedAndNothingElse() throws Exception {
        Map<String, Long> before = businessCounts();
        Map<String, Long> sideEffectsBefore = sideEffectCounts();
        long batchId = importPeopleAndSubject();
        Map<String, Long> afterCommit = businessCounts();
        assertThat(afterCommit.get("person")).isEqualTo(before.get("person") + 2);
        assertThat(afterCommit.get("student")).isEqualTo(before.get("student") + 1);
        assertThat(afterCommit.get("instructor")).isEqualTo(before.get("instructor") + 1);
        assertThat(afterCommit.get("subject")).isEqualTo(before.get("subject") + 1);

        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRollback").value(true))
                .andExpect(jsonPath("$.created.length()").value(1)).andExpect(jsonPath("$.retained.length()").value(1))
                .andExpect(jsonPath("$.retained[0].entityType").value("SUBJECTS")).andExpect(jsonPath("$.blockers.length()").value(0));
        assertThat(businessCounts()).isEqualTo(afterCommit);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        Map<String, Long> expected = new TreeMap<>(before);
        expected.put("subject", before.get("subject") + 1);
        assertThat(businessCounts()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ? and entity_type = 'SUBJECTS'", Integer.class, batchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ? and entity_type <> 'SUBJECTS'", Integer.class, batchId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'COMMITTED'", Integer.class, batchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'VALID'", Integer.class, batchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isEqualTo(1);
        assertThat(sideEffectCounts()).isEqualTo(sideEffectsBefore);
        assertThat(auditActions(batchId)).containsExactly("DATA_IMPORT_CREATED", "DATA_IMPORT_COMMITTED", "DATA_IMPORT_ROLLED_BACK");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'DATA_IMPORT_BATCH' and entity_id = ? and actor_role = 'DIRECTOR' and actor_person_id is not null and reason is null", Integer.class, batchId)).isEqualTo(3);
    }

    @Test
    void rollbackKeepsTheRetainedStructureAndUndoesEverythingElse() throws Exception {
        Map<String, Long> before = businessCounts();
        List<Long> termIdsBefore = termIds();
        long batchId = importFullHistory(suffix, studentDni, teacherDni);
        Map<String, Long> afterCommit = businessCounts();

        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRollback").value(true))
                .andExpect(jsonPath("$.created.length()").value(5)).andExpect(jsonPath("$.retained.length()").value(4))
                .andExpect(jsonPath("$.blockers.length()").value(0));
        assertThat(businessCounts()).isEqualTo(afterCommit);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        Map<String, Long> afterRollback = businessCounts();
        for (String table : List.of("program", "price", "price_version", "subject", "program_subject", "program_subject_prerequisite")) {
            assertThat(afterRollback.get(table)).as(table).isEqualTo(afterCommit.get(table));
        }
        for (String table : List.of("person", "address", "student", "instructor", "academic_offering_cycle", "subject_section",
                "subject_section_instructor", "program_enrollment", "subject_section_enrollment", "commission_closure",
                "commission_closure_student")) {
            assertThat(afterRollback.get(table)).as(table).isEqualTo(before.get(table));
        }
        assertThat(newTermsNotPinnedByRetainedStructure(termIdsBefore)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Integer.class, batchId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'COMMITTED'", Integer.class, batchId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ? and status = 'VALID'", Integer.class, batchId)).isEqualTo(7);
    }

    @Test
    void rollbackBlockedByAccessGrantedToAnImportedPersonChangesNothing() throws Exception {
        long batchId = importPeopleAndSubject();
        Map<String, Long> afterCommit = businessCounts();
        jdbc.update("update person set grant_system_access = true where dni = ?", studentDni);

        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRollback").value(false))
                .andExpect(jsonPath("$.blockers[0].entityType").value("PERSONS"));
        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_ROLLBACK_BLOCKED"))
                .andExpect(jsonPath("$.details.blockers[0].entityType").value("PERSONS"));

        assertThat(businessCounts()).isEqualTo(afterCommit);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Integer.class, batchId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLBACK_BLOCKED'", Integer.class, batchId)).isEqualTo(1);
        assertThat(auditActions(batchId)).contains("DATA_IMPORT_ROLLBACK_BLOCKED").doesNotContain("DATA_IMPORT_ROLLED_BACK");
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isZero();
    }

    @Test
    void rollbackBlockedByLaterRowsReferencingImportedDataChangesNothing() throws Exception {
        long batchId = importPeopleAndSubject();
        Map<String, Long> afterCommit = businessCounts();
        Long actorId = jdbc.queryForObject("select created_by_person_id from import_batch where id = ?", Long.class, batchId);
        jdbc.update("insert into student_status_change (student_id, change_type, reason, actor_person_id, occurred_at) select s.id, 'WITHDRAWN', 'later', ?, now() from student s join person p on p.id = s.person_id where p.dni = ?",
                actorId, studentDni);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_ROLLBACK_BLOCKED"));

        assertThat(businessCounts()).isEqualTo(afterCommit);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("COMMITTED");
        jdbc.update("delete from student_status_change where reason = 'later'");
    }

    @Test
    void rollbackAndSealRequireTheWrittenWord() throws Exception {
        long batchId = importFullHistory(suffix, studentDni, teacherDni);
        Map<String, Long> afterCommit = businessCounts();

        postWithConfirmation(batchId, "rollback", "CERRAR").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_CONFIRMATION_INVALID"));
        postWithConfirmation(batchId, "seal", "DESHACER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_CONFIRMATION_INVALID"));
        postWithConfirmation(batchId, "seal", "  ").andExpect(status().isBadRequest());

        assertThat(businessCounts()).isEqualTo(afterCommit);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("COMMITTED");
    }

    @Test
    void sealedBatchCannotBeRolledBackSealedAgainOrReceiveFiles() throws Exception {
        long batchId = importFullHistory(suffix, studentDni, teacherDni);
        Map<String, Long> afterCommit = businessCounts();

        postWithConfirmation(batchId, "seal", "cerrar").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEALED")).andExpect(jsonPath("$.sealedAt").isNotEmpty());

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_STATE_INVALID"));
        postWithConfirmation(batchId, "seal", "CERRAR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_STATE_INVALID"));
        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/SUBJECTS")
                .file(new MockMultipartFile("file", "SUBJECTS.csv", "text/csv",
                        "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\nZZ99,Otra,4,NUMERIC,3,ACTIVE\n".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer())).andExpect(status().isConflict());

        assertThat(businessCounts()).isEqualTo(afterCommit);
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'SEALED'", Integer.class, batchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sealed_by_person_id from import_batch where id = ?", Long.class, batchId)).isNotNull();
    }

    @Test
    void batchNotYetCommittedCannotBeSealedOrRolledBack() throws Exception {
        long batchId = createBatch();
        postWithConfirmation(batchId, "seal", "CERRAR").andExpect(status().isConflict());
        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isConflict());
        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", bearer())).andExpect(status().isConflict());
    }

    @Test
    void partialBatchRetryLoadsOnlyPendingRowsAndIsIdempotent() throws Exception {
        String blockerDni = studentDni;
        long blocker = createBatch();
        upload(blocker, "PERSONS", PERSON_HEADER + student(blockerDni, "Bloquea", "blocker." + suffix));
        validate(blocker).andExpect(jsonPath("$.status").value("VALIDATED"));

        long batchId = createBatch();
        upload(batchId, "PERSONS", PERSON_HEADER + student(blockerDni, "Ana", "ana." + suffix)
                + student(teacherDni, "Beto", "beto." + suffix));
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + "P" + suffix.substring(0, 3) + ",Materia parcial " + suffix + ",4,NUMERIC,3,ACTIVE\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(blocker).andExpect(jsonPath("$.status").value("COMMITTED"));

        commit(batchId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"));
        assertThat(rowStatuses(batchId, "PERSONS")).containsExactly("FAILED", "FAILED");
        assertThat(rowStatuses(batchId, "SUBJECTS")).containsExactly("COMMITTED");
        assertThat(subjectCount("P" + suffix.substring(0, 3))).isEqualTo(1);

        mockMvc.perform(get("/data-imports/" + batchId + "/report.csv").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("﻿entityType,rowNumber,naturalKey,status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CHUNK_FAILED")));
        assertThat(auditActions(batchId)).contains("DATA_IMPORT_REPORT_DOWNLOADED");


        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIAL"));
        assertThat(rowStatuses(batchId, "PERSONS")).containsExactly("FAILED", "FAILED");
        assertThat(subjectCount("P" + suffix.substring(0, 3))).isEqualTo(1);

        postWithConfirmation(blocker, "rollback", "DESHACER").andExpect(status().isOk());
        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));
        assertThat(rowStatuses(batchId, "PERSONS")).containsExactly("COMMITTED", "COMMITTED");
        assertThat(subjectCount("P" + suffix.substring(0, 3))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from person where dni in (?, ?)", Integer.class, blockerDni, teacherDni)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'RETRIED'", Integer.class, batchId)).isEqualTo(2);

        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", bearer())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from person where dni in (?, ?)", Integer.class, blockerDni, teacherDni)).isEqualTo(2);
    }

    @Test
    void templatesMatchTheColumnsEachBlockExpects() throws Exception {
        for (ImportEntityType type : ImportEntityType.values()) {
            MvcResult result = mockMvc.perform(get("/data-imports/templates/" + type.name()).header("Authorization", bearer()))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv")).andReturn();
            String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            String header = body.replace("﻿", "").split("\\R")[0];
            assertThat(Arrays.asList(header.split(","))).as(type.name()).isEqualTo(registry.definitionFor(type).columns());
            assertThat(body.split("\\R").length).as(type.name()).isGreaterThan(1);
        }
    }

    @Test
    void reportListsEveryRowWithItsStatusAndCreatedEntity() throws Exception {
        long batchId = importFullHistory(suffix, studentDni, teacherDni);

        MvcResult result = mockMvc.perform(get("/data-imports/" + batchId + "/report.csv").header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn();
        String[] lines = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).split("\\R");

        assertThat(lines).hasSize(14);
        assertThat(Arrays.stream(lines).skip(1).allMatch(line -> line.contains(",COMMITTED,") && !line.endsWith(","))).isTrue();
    }

    @Test
    void newEndpointsAreReservedForDirectors() throws Exception {
        long batchId = createBatch();
        String studentToken = tokenFor("ALUMNO");
        mockMvc.perform(post("/data-imports/" + batchId + "/seal").header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"CERRAR\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/data-imports/" + batchId + "/report.csv").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        assertThat(auditActions(batchId)).containsExactly("DATA_IMPORT_CREATED");
        mockMvc.perform(get("/data-imports/templates/PERSONS").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rollbackDeletesTheTermTheBatchCreatedSoNoEmptyTermIsLeftBehind() throws Exception {
        int year = 2010;
        int termsBefore = termCount(year, "FIRST_SEMESTER");
        long batchId = importProgramWithCycle(suffix, year, "FIRST_SEMESTER");
        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore + 1);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore);
        assertThat(jdbc.queryForObject("select count(*) from academic_offering_cycle c join program p on p.id = c.program_id where p.name = ?",
                Integer.class, "Carrera " + suffix)).isZero();
        assertThat(jdbc.queryForObject("select detail->>'TERMS' from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'",
                String.class, batchId)).isEqualTo("1");
    }

    @Test
    void rollbackKeepsATermThatAlreadyExistedBeforeTheBatch() throws Exception {
        int year = 2011;
        jdbc.update("insert into term (year_number, type, description, start_date, end_date, status, enrollment_start, enrollment_end) "
                + "values (?, 'ANNUAL', 'Previo', ?::timestamp, ?::timestamp, 'ACTIVE', ?::timestamp, ?::timestamp)",
                year, year + "-03-01", year + "-12-15", year + "-03-01", year + "-12-15");
        int termsBefore = termCount(year, "ANNUAL");
        long batchId = importProgramWithCycle(suffix, year, "ANNUAL");
        assertThat(termCount(year, "ANNUAL")).isEqualTo(termsBefore);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk());

        assertThat(termCount(year, "ANNUAL")).isEqualTo(termsBefore);
    }

    @Test
    void rollbackKeepsTheTermWhileAnotherBatchStillUsesIt() throws Exception {
        int year = 2012;
        int termsBefore = termCount(year, "FIRST_SEMESTER");
        long first = importProgramWithCycle(suffix, year, "FIRST_SEMESTER");
        long second = importProgramWithCycle(suffix + "b", year, "FIRST_SEMESTER");
        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore + 1);

        postWithConfirmation(first, "rollback", "DESHACER").andExpect(status().isOk());

        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore + 1);
        assertThat(jdbc.queryForObject("select count(*) from academic_offering_cycle c join term t on t.id = c.term_id where t.year_number = ? and t.type = 'FIRST_SEMESTER'",
                Integer.class, year)).isEqualTo(1);
        postWithConfirmation(second, "rollback", "DESHACER").andExpect(status().isOk());
    }

    @Test
    void rollbackDeletesASharedTermOnlyAfterTheLastCycleOfTheBatchIsGone() throws Exception {
        int year = 2013;
        int termsBefore = termCount(year, "FIRST_SEMESTER");
        long batchId = createBatch();
        upload(batchId, "PROGRAMS", PROGRAM_HEADER + "Carrera " + suffix + "a,UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n"
                + "Carrera " + suffix + "b,UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + ",FIRST_SEMESTER,Ciclo," + year + "-03-15,15/07/" + year + ",Carrera " + suffix + "a\n"
                + year + ",FIRST_SEMESTER,Ciclo," + year + "-03-15,15/07/" + year + ",Carrera " + suffix + "b\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(status().isOk());
        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore + 1);

        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created.length()").value(1))
                .andExpect(jsonPath("$.created[0].total").value(2));
        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk());

        assertThat(termCount(year, "FIRST_SEMESTER")).isEqualTo(termsBefore);
    }

    @Test
    void rollbackLeftInRollingBackByACutIsResumedAndCompletesOnce() throws Exception {
        Map<String, Long> before = businessCounts();
        List<Long> termIdsBefore = termIds();
        long batchId = importFullHistory(suffix, studentDni, teacherDni, 2014);
        Map<String, Long> afterCommit = businessCounts();

        lifecycleRecorder.startRollback(batchId);

        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("ROLLING_BACK");
        assertThat(businessCounts()).isEqualTo(afterCommit);
        mockMvc.perform(get("/data-imports/" + batchId + "/rollback-preview").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRollback").value(true));

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        assertThat(businessCounts().get("person")).isEqualTo(before.get("person"));
        assertThat(newTermsNotPinnedByRetainedStructure(termIdsBefore)).isEmpty();
        assertThat(businessCounts().get("subject_section_enrollment")).isEqualTo(before.get("subject_section_enrollment"));
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isEqualTo(1);
        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isConflict());
    }

    @Test
    void cutInTheMiddleOfTheUndoLeavesEverythingIntactAndRetryFinishesIt() throws Exception {
        Long actor = jdbc.queryForObject("select id from person order by id desc limit 1", Long.class);
        Map<String, Long> before = businessCounts();
        List<Long> termIdsBefore = termIds();
        long batchId = importFullHistory(suffix, studentDni, teacherDni, 2014);
        Map<String, Long> afterCommit = businessCounts();
        lifecycleRecorder.startRollback(batchId);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            rollbackTransaction.execute(batchId, actor);
            throw new IllegalStateException("cut");
        })).hasMessage("cut");

        assertThat(businessCounts()).isEqualTo(afterCommit);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("ROLLING_BACK");
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isZero();

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        assertThat(businessCounts().get("person")).isEqualTo(before.get("person"));
        assertThat(newTermsNotPinnedByRetainedStructure(termIdsBefore)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isEqualTo(1);
    }

    @Test
    void secondExecutionOfAnAlreadyFinishedUndoChangesNothing() throws Exception {
        long batchId = importPeopleAndSubject();
        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk());
        Map<String, Long> afterRollback = businessCounts();

        assertThat(rollbackTransaction.execute(batchId, 1L)).isEmpty();

        assertThat(businessCounts()).isEqualTo(afterRollback);
        assertThat(jdbc.queryForObject("select count(*) from import_batch_event where batch_id = ? and type = 'ROLLED_BACK'", Integer.class, batchId)).isEqualTo(1);
    }

    private long importPeopleAndSubject() throws Exception {
        long batchId = createBatch();
        upload(batchId, "PERSONS", PERSON_HEADER + student(studentDni, "Ana", "ana." + suffix)
                + "DNI," + teacherDni + ",Marta,Ríos,INSTRUCTOR,F,1978-11-30,marta." + suffix + "@example.com,3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n");
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + "H" + suffix.substring(0, 3) + ",Materia " + suffix + ",4,NUMERIC,3,ACTIVE\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));
        return batchId;
    }

    private long importFullHistory(String key, String studentDocument, String teacherDocument) throws Exception {
        return importFullHistory(key, studentDocument, teacherDocument, 2015 + ThreadLocalRandom.current().nextInt(1, 5));
    }

    private long importProgramWithCycle(String key, int year, String termType) throws Exception {
        long batchId = createBatch();
        upload(batchId, "PROGRAMS", PROGRAM_HEADER + "Carrera " + key + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + "," + termType + ",Ciclo," + year + "-03-15,15/07/" + year + ",Carrera " + key + "\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));
        return batchId;
    }

    private List<Long> termIds() {
        return jdbc.queryForList("select id from term", Long.class);
    }

    private List<Long> newTermsNotPinnedByRetainedStructure(List<Long> termIdsBefore) {
        return termIds().stream().filter(id -> !termIdsBefore.contains(id))
                .filter(id -> jdbc.queryForObject("select count(*) from program_year_billing_template where term_id = ?", Integer.class, id) == 0)
                .toList();
    }

    private int termCount(int year, String termType) {
        return jdbc.queryForObject("select count(*) from term where year_number = ? and type = ?", Integer.class, year, termType);
    }

    private long importFullHistory(String key, String studentDocument, String teacherDocument, int year) throws Exception {
        String program = "Tecnicatura " + key;
        String subject = "H" + key.substring(0, 3);
        String second = "J" + key.substring(0, 3);
        String section = "SEC-" + key;
        long batchId = createBatch();
        upload(batchId, "PERSONS", PERSON_HEADER + student(studentDocument, "Ana", "ana." + key)
                + "DNI," + teacherDocument + ",Marta,Ríos,INSTRUCTOR,F,1978-11-30,marta." + key + "@example.com,3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n");
        upload(batchId, "PROGRAMS", "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n"
                + program + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + subject + ",Materia " + key + ",4,NUMERIC,3,ACTIVE\n"
                + second + ",Materia dos " + key + ",4,NUMERIC,3,ACTIVE\n");
        upload(batchId, "CURRICULUM", "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n"
                + program + "," + subject + ",1,FIRST,6,MANDATORY,PROMOTIONAL,1\n"
                + program + "," + second + ",2,FIRST,6,MANDATORY,PROMOTIONAL,2\n");
        upload(batchId, "PREREQUISITES", "programName,subjectCode,prerequisiteCode,requirementType,minGrade\n"
                + program + "," + second + "," + subject + ",APPROVED,4\n");
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + "," + program + "\n"
                + year + ",ANNUAL,Anual," + year + "-03-01,15/12/" + year + "," + program + "\n");
        upload(batchId, "SECTIONS", "sectionCode,subjectCode,year,termType,name,shift,instructorDni,closedOn,actaNumber,actaBook,actaFolio,programName\n"
                + section + "," + subject + "," + year + ",FIRST_SEMESTER,Comisión A,MORNING," + teacherDocument + "," + year + "-07-20,,Libro 7,Folio 45,\n");
        upload(batchId, "PROGRAM_ENROLLMENTS", "documentType,dni,programName,enrollmentDate,status,completionDate,withdrawalDate,cohortYear,intake\n"
                + "DNI," + studentDocument + "," + program + "," + year + "-03-01,ENROLLED,,," + year + ",FIRST\n");
        upload(batchId, "SECTION_ENROLLMENTS", "documentType,dni,programName,sectionCode,attemptNumber,status,finalScore,finalGradeType,finalDate\n"
                + "DNI," + studentDocument + "," + program + "," + section + ",1,APPROVED,\"8,50\",PROMOTION," + year + "-07-20\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));
        return batchId;
    }

    private static String student(String dni, String firstName, String emailLocalPart) {
        return "DNI," + dni + "," + firstName + ",Pérez,STUDENT,F,1990-01-01," + emailLocalPart + "@example.com,3512345678,Argentina,Av. Colón 1,Córdoba,Córdoba,5000,,2016-03-01,ACTIVE,,,,,,,,,,,,\n";
    }

    private ResultActions validate(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    private ResultActions commit(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions postWithConfirmation(long batchId, String action, String word) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/" + action).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"" + word + "\"}"));
    }

    private List<String> rowStatuses(long batchId, String type) {
        return jdbc.queryForList("select status from import_row where batch_id = ? and entity_type = ? order by row_number", String.class, batchId, type);
    }

    private int subjectCount(String code) {
        return jdbc.queryForObject("select count(*) from subject where code = ?", Integer.class, code);
    }

    private String bearer() {
        return "Bearer " + directorToken;
    }

    private String tokenFor(String roleName) {
        return transactions.execute(status -> jwtService.generateAccessToken(persistPerson(roleName)));
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ciclo de vida\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void upload(long batchId, String type, String csv) throws Exception {
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/" + type)
                .file(new MockMultipartFile("file", type + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer())).andExpect(status().isOk());
    }

    private Map<String, Long> businessCounts() {
        return counts(BUSINESS_TABLES);
    }

    private Map<String, Long> sideEffectCounts() {
        return counts(SIDE_EFFECT_TABLES);
    }

    private Map<String, Long> counts(List<String> tables) {
        Map<String, Long> counts = new TreeMap<>();
        tables.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class)));
        return counts;
    }

    private List<String> auditActions(long batchId) {
        return jdbc.queryForList("select action from audit_log where entity_type = 'DATA_IMPORT_BATCH' and entity_id = ? order by id", String.class, batchId);
    }

    private void cleanImportTables() {
        jdbc.update("delete from import_created_entity");
        jdbc.update("delete from import_row");
        jdbc.update("delete from import_file");
        jdbc.update("delete from import_batch_event");
        jdbc.update("delete from import_batch");
    }

    private static String uniqueDni() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
    }

    private Person persistDirector() {
        return persistPerson("DIRECTOR");
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String unique = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(unique);
        person.setDni(unique.substring(0, 12));
        person.setUsername("dataimport." + unique);
        person.setEmail("dataimport." + unique + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
