package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
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
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DataImportGraduationsIT extends AbstractIntegrationTest {
    private static final String PERSON_HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone\n";
    private static final String ENROLLMENT_HEADER = "documentType,dni,programName,enrollmentDate,status,completionDate,withdrawalDate,cohortYear,intake\n";
    private static final String GRADUATION_HEADER = "documentType,dni,programName,graduationDate,registryNumber,legacyTitleNumber\n";
    private static final List<String> SIDE_EFFECT_TABLES = List.of("notification", "notification_delivery",
            "event_publication", "invoice", "payment", "current_account_entry", "graduation_certificate");

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

    private String directorToken;
    private String suffix;
    private String program;
    private String studentDni;
    private int year;

    @BeforeEach
    void prepare() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
        cleanImportTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        program = "Tecnicatura " + suffix;
        studentDni = uniqueDni();
        year = 2015 + ThreadLocalRandom.current().nextInt(1, 5);
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

    private void cleanImportTables() {
        jdbc.update("delete from import_created_entity");
        jdbc.update("delete from import_row");
        jdbc.update("delete from import_file");
        jdbc.update("delete from import_batch_event");
        jdbc.update("delete from import_batch");
    }

    @Test
    void graduationsImportedTogetherWithTheirEnrollmentKeepTheRegistryNumberAndRollBackCleanly() throws Exception {
        Map<String, Long> sideEffects = counts(SIDE_EFFECT_TABLES);
        long graduationsBefore = count("graduation_request");
        long batchId = createBatch();
        uploadStudentAndCompletedEnrollment(batchId, "COMPLETED");
        upload(batchId, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-2024-0317,T-5521\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        assertThat(count("graduation_request")).isEqualTo(graduationsBefore);
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.graduationsPendingReview").value(1));
        mockMvc.perform(get("/data-imports/" + batchId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.graduationsPendingReview").value(1));
        jdbc.update("update graduation_request set certificate_review_status = 'ENABLED', certificate_reviewed_at = now(), certificate_reviewed_by_id = (select id from person limit 1) where registry_number = 'REG-2024-0317'");
        mockMvc.perform(get("/data-imports/" + batchId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.graduationsPendingReview").value(0));
        jdbc.update("update graduation_request set certificate_review_status = 'PENDING_REVIEW', certificate_reviewed_at = null, certificate_reviewed_by_id = null where registry_number = 'REG-2024-0317'");

        Map<String, Object> graduation = graduationOf(studentDni);
        assertThat(graduation.get("status")).isEqualTo("APPROVED");
        assertThat(graduation.get("registry_number")).isEqualTo("REG-2024-0317");
        assertThat(graduation.get("legacy_title_number")).isEqualTo("T-5521");
        assertThat(graduation.get("requested_at").toString()).startsWith(year + "-12-10 12:00");
        assertThat(graduation.get("decided_at").toString()).startsWith(year + "-12-10 12:00");
        assertThat(graduation.get("decided_by_id")).isNull();
        assertThat(graduation.get("verified_by_id")).isNull();
        assertThat(count("graduation_request")).isEqualTo(graduationsBefore + 1);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        assertThat(count("graduation_request")).isEqualTo(graduationsBefore);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ? and entity_type = 'GRADUATIONS'", Integer.class, batchId)).isZero();
    }

    @Test
    void rollingBackTheGraduationsBatchDeletesOnlyTheGraduationAndKeepsTheEnrollment() throws Exception {
        long first = createBatch();
        uploadStudentAndCompletedEnrollment(first, "COMPLETED");
        validate(first).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(first).andExpect(jsonPath("$.status").value("COMMITTED"));
        long unrelatedGraduationId = insertUnrelatedGraduation();
        long graduationsBefore = count("graduation_request");

        long second = createBatch();
        upload(second, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10,,\n");
        validate(second).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(second).andExpect(jsonPath("$.status").value("COMMITTED"));
        assertThat(graduationOf(studentDni).get("registry_number")).isNull();
        assertThat(graduationOf(studentDni).get("legacy_title_number")).isNull();
        assertThat(count("graduation_request")).isEqualTo(graduationsBefore + 1);

        postWithConfirmation(second, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

        assertThat(count("graduation_request")).isEqualTo(graduationsBefore);
        assertThat(jdbc.queryForObject("select count(*) from graduation_request where id = ?", Integer.class, unrelatedGraduationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from person where dni = ?", Integer.class, studentDni)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select pe.status from program_enrollment pe join program p on p.id = pe.program_id where p.name = ?", String.class, program)).isEqualTo("COMPLETED");
    }

    @Test
    void retryAfterACutLoadsTheGraduationOnceTheBlockerIsGone() throws Exception {
        long first = createBatch();
        uploadStudentAndCompletedEnrollment(first, "COMPLETED");
        validate(first);
        commit(first).andExpect(jsonPath("$.status").value("COMMITTED"));
        long batchId = createBatch();
        upload(batchId, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-1,\n");
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        long blockerId = insertBlockingGraduation();

        commit(batchId).andExpect(jsonPath("$.status").value("PARTIAL"));
        assertThat(rowStatuses(batchId, "GRADUATIONS")).containsExactly("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from graduation_request where registry_number = 'REG-1'", Integer.class)).isZero();

        jdbc.update("delete from graduation_request where id = ?", blockerId);
        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));
        assertThat(rowStatuses(batchId, "GRADUATIONS")).containsExactly("COMMITTED");
        mockMvc.perform(post("/data-imports/" + batchId + "/retry").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from graduation_request where registry_number = 'REG-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidGraduationsAreReportedPerRowAndNothingIsWritten() throws Exception {
        long graduationsBefore = count("graduation_request");
        long batchId = createBatch();
        uploadStudentAndCompletedEnrollment(batchId, "ENROLLED");
        upload(batchId, "GRADUATIONS", GRADUATION_HEADER
                + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-1,\n"
                + "DNI," + studentDni + "," + program + "," + year + "-12-11,REG-2,\n"
                + "DNI," + uniqueDni() + "," + program + "," + year + "-12-10,REG-3,\n"
                + "DNI," + studentDni + "," + program + ",2999-12-10,REG-4,\n"
                + "DNI," + studentDni + ",Otra carrera " + suffix + "," + year + "-12-10,REG-5,\n"
                + "DNI," + studentDni + "," + program + ",,REG-6,\n"
                + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-0123456789012345678,T-" + "9".repeat(50) + "\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));

        assertThat(rowCodes(batchId, "GRADUATIONS")).contains("COMPLETED_PROGRAM_ENROLLMENT_NOT_FOUND",
                "GRADUATION_DATE_IN_FUTURE", "GRADUATION_DATE_REQUIRED", "REGISTRYNUMBER_TOO_LONG",
                "LEGACYTITLENUMBER_TOO_LONG", "STUDENTPROGRAMGRADUATION_DUPLICATED_IN_FILE");
        assertThat(rowStatuses(batchId, "GRADUATIONS")).containsOnly("ERROR");
        assertThat(count("graduation_request")).isEqualTo(graduationsBefore);
    }

    @Test
    void maximumLengthNumbersAreAcceptedAndStored() throws Exception {
        long batchId = createBatch();
        uploadStudentAndCompletedEnrollment(batchId, "COMPLETED");
        String registry = "R".repeat(20);
        String legacy = "T".repeat(50);
        upload(batchId, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10," + registry + "," + legacy + "\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));

        assertThat(graduationOf(studentDni).get("registry_number")).isEqualTo(registry);
        assertThat(graduationOf(studentDni).get("legacy_title_number")).isEqualTo(legacy);
    }

    @Test
    void graduationWithADifferentDateThanTheCompletedEnrollmentIsRejectedAndExistingOnesAreConflicts() throws Exception {
        long first = createBatch();
        uploadStudentAndCompletedEnrollment(first, "COMPLETED");
        upload(first, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-1,\n");
        validate(first);
        commit(first).andExpect(jsonPath("$.status").value("COMMITTED"));

        long second = createBatch();
        upload(second, "GRADUATIONS", GRADUATION_HEADER + "DNI," + studentDni + "," + program + "," + year + "-12-10,REG-2,\n"
                + "DNI," + studentDni + "," + program + "," + year + "-11-01,REG-3,\n");
        validate(second);

        assertThat(rowStatuses(second, "GRADUATIONS")).containsExactly("CONFLICT", "ERROR");
        assertThat(rowCodes(second, "GRADUATIONS")).contains("GRADUATION_ALREADY_EXISTS", "COMPLETED_PROGRAM_ENROLLMENT_NOT_FOUND");
    }

    private void uploadStudentAndCompletedEnrollment(long batchId, String enrollmentStatus) throws Exception {
        upload(batchId, "PERSONS", PERSON_HEADER
                + "DNI," + studentDni + ",Ana,Pérez,STUDENT,F,1990-01-01,ana." + suffix + "@example.com,3512345678,Argentina,Av. Colón 1,Córdoba,Córdoba,5000,," + year + "-03-01,ACTIVE,,,,,,,,,,,,\n");
        upload(batchId, "PROGRAMS", "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n"
                + program + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        String completion = enrollmentStatus.equals("COMPLETED") ? year + "-12-10" : "";
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + ",ANNUAL,Anual," + year + "-03-01,15/12/" + year + "," + program + "\n");
        upload(batchId, "PROGRAM_ENROLLMENTS", ENROLLMENT_HEADER
                + "DNI," + studentDni + "," + program + "," + year + "-03-01," + enrollmentStatus + "," + completion + ",," + year + ",FIRST\n");
    }

    private long insertUnrelatedGraduation() {
        Long enrollmentId = jdbc.queryForObject("select pe.id from program_enrollment pe join program p on p.id = pe.program_id where p.name = ?", Long.class, program);
        return jdbc.queryForObject("insert into graduation_request (program_enrollment_id, status, requested_at, version) values (?, 'REJECTED', now(), 0) returning id", Long.class, enrollmentId);
    }

    private long insertBlockingGraduation() {
        return transactions.execute(status -> {
            Long enrollmentId = jdbc.queryForObject("select pe.id from program_enrollment pe join program p on p.id = pe.program_id where p.name = ?", Long.class, program);
            return jdbc.queryForObject("insert into graduation_request (program_enrollment_id, status, requested_at, version) values (?, 'REQUESTED', now(), 0) returning id", Long.class, enrollmentId);
        });
    }

    private Map<String, Object> graduationOf(String dni) {
        return jdbc.queryForMap("select gr.* from graduation_request gr join program_enrollment pe on pe.id = gr.program_enrollment_id join program p on p.id = pe.program_id where p.name = ? and gr.status = 'APPROVED'", program);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private org.springframework.test.web.servlet.ResultActions validate(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions commit(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions postWithConfirmation(long batchId, String action, String word) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/" + action).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"" + word + "\"}"));
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Egresos\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void upload(long batchId, String type, String csv) throws Exception {
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/" + type)
                .file(new MockMultipartFile("file", type + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
    }

    private List<String> rowStatuses(long batchId, String type) {
        return jdbc.queryForList("select status from import_row where batch_id = ? and entity_type = ? order by row_number", String.class, batchId, type);
    }

    private List<String> rowCodes(long batchId, String type) {
        return jdbc.queryForList("select jsonb_array_elements(errors)->>'code' from import_row where batch_id = ? and entity_type = ?", String.class, batchId, type);
    }

    private Map<String, Long> counts(List<String> tables) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        tables.forEach(table -> counts.put(table, count(table)));
        return counts;
    }

    private static String uniqueDni() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
    }

    private Person persistDirector() {
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
        Role role = roleRepository.findByName("DIRECTOR").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
