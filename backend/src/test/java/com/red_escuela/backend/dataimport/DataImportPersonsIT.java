package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
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
class DataImportPersonsIT extends AbstractIntegrationTest {
    private static final String HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone";
    private static final List<String> SIDE_EFFECT_TABLES = List.of("notification", "notification_delivery",
            "event_publication", "email_verification_token", "password_reset_token", "invoice", "payment",
            "current_account_entry");

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
    private String teacherToken;

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    @Test
    void batchCannotBeCreatedWhileBillingIsNotOff() throws Exception {
        setBillingMode("MIRROR");

        mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILLING_MODE_NOT_OFF"));
    }

    @Test
    void commitIsRejectedIfBillingWasTurnedOnAfterValidation() throws Exception {
        long batchId = createBatch();
        upload(batchId, HEADER + "\n" + studentRow(uniqueDni(), "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);
        validate(batchId);
        setBillingMode("FULL");

        commit(batchId, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILLING_MODE_NOT_OFF"));
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity", Integer.class)).isZero();
    }

    @BeforeEach
    void createInstitutionAndUsers() {
        setBillingMode("OFF");
        cleanImportTables();
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
            teacherToken = jwtService.generateAccessToken(persistPerson("DOCENTE"));
        });
    }

    @AfterEach
    void restoreBillingModeAndCleanImportTables() {
        cleanImportTables();
        setBillingMode("FULL");
    }

    private void cleanImportTables() {
        jdbc.update("delete from import_created_entity");
        jdbc.update("delete from import_row");
        jdbc.update("delete from import_file");
        jdbc.update("delete from import_batch_event");
        jdbc.update("delete from import_batch");
    }

    @Test
    void importsStudentsAndInstructorsWithoutAccessOrSideEffects() throws Exception {
        String studentDni = uniqueDni();
        String instructorDni = uniqueDni();
        String studentEmail = uniqueEmail();
        String instructorEmail = uniqueEmail();
        String csv = HEADER + "\n"
                + studentRow(studentDni, "Lucía", "Fernández", "STUDENT", "1992-04-18", studentEmail, "15/03/2022")
                + instructorRow(instructorDni, instructorEmail);
        Map<String, Long> before = sideEffectCounts();

        long batchId = createBatch();
        upload(batchId, csv, 200);
        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        commit(batchId, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));

        Map<String, Object> student = jdbc.queryForMap(
                "select p.password, p.grant_system_access, p.email_verified, p.name, p.last_name, p.status from person p where p.dni = ?",
                studentDni);
        assertThat(student.get("password")).isNull();
        assertThat(student.get("grant_system_access")).isEqualTo(false);
        assertThat(student.get("email_verified")).isEqualTo(false);
        assertThat(student.get("name")).isEqualTo("Lucía");
        assertThat(jdbc.queryForObject("select s.registered_at::date::text from student s join person p on p.id = s.person_id where p.dni = ?",
                String.class, studentDni)).isEqualTo("2022-03-15");
        assertThat(jdbc.queryForObject("select count(*) from instructor i join person p on p.id = i.person_id where p.dni = ?",
                Integer.class, instructorDni)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from person_role pr join person p on p.id = pr.person_id join role r on r.id = pr.role_id where p.dni = ? and r.name = 'DOCENTE'",
                Integer.class, instructorDni)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where entity_type = 'PERSONS'", Integer.class)).isEqualTo(2);
        assertThat(sideEffectCounts()).isEqualTo(before);
    }

    @Test
    void dryRunWritesNothingToBusinessTables() throws Exception {
        long batchId = createBatch();
        upload(batchId, HEADER + "\n" + studentRow(uniqueDni(), "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);
        long people = jdbc.queryForObject("select count(*) from person", Long.class);
        long students = jdbc.queryForObject("select count(*) from student", Long.class);
        Map<String, Long> before = sideEffectCounts();

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.counters[0].rowsByStatus.VALID").value(1));

        assertThat(jdbc.queryForObject("select count(*) from person", Long.class)).isEqualTo(people);
        assertThat(jdbc.queryForObject("select count(*) from student", Long.class)).isEqualTo(students);
        assertThat(sideEffectCounts()).isEqualTo(before);
    }

    @Test
    void personWithoutEmailStaysOutAndOnlyValidRowsAreCommitted() throws Exception {
        String okDni = uniqueDni();
        String noMailDni = uniqueDni();
        String csv = HEADER + "\n"
                + studentRow(okDni, "Ok", "Uno", "STUDENT", "1990-01-01", uniqueEmail(), "")
                + studentRow(noMailDni, "Sin", "Correo", "STUDENT", "1990-01-01", "", "");
        long batchId = createBatch();
        upload(batchId, csv, 200);

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));
        commit(batchId, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_NOT_VALIDATED"));
        mockMvc.perform(get("/data-imports/" + batchId + "/errors").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("EMAIL_REQUIRED"))
                .andExpect(jsonPath("$.items[0].rowNumber").value(3));
        commit(batchId, "{\"onlyValidRows\":true}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from person where dni = ?", Integer.class, okDni)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from person where dni = ?", Integer.class, noMailDni)).isZero();
    }

    @Test
    void duplicatedDocumentAndEmailInsideTheFileAreReportedOnTheSecondOccurrence() throws Exception {
        String dni = uniqueDni();
        String email = uniqueEmail();
        String csv = HEADER + "\n"
                + studentRow(dni, "Uno", "A", "STUDENT", "1990-01-01", email, "")
                + studentRow(dni, "Dos", "B", "STUDENT", "1990-01-01", email, "");
        long batchId = createBatch();
        upload(batchId, csv, 200);

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/data-imports/" + batchId + "/errors").header("Authorization", "Bearer " + directorToken))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rowNumber").value(3))
                .andExpect(jsonPath("$.items[0].code").value("DNI_DUPLICATED_IN_FILE"))
                .andExpect(jsonPath("$.items[1].code").value("EMAIL_DUPLICATED_IN_FILE"));
    }

    @Test
    void importingTheSameFileAgainClassifiesRowsAsUnchangedAndCreatesNothing() throws Exception {
        String csv = HEADER + "\n" + studentRow(uniqueDni(), "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), "");
        long first = createBatch();
        upload(first, csv, 200);
        validate(first);
        commit(first, "{}").andExpect(jsonPath("$.status").value("COMMITTED"));
        long people = jdbc.queryForObject("select count(*) from person", Long.class);

        long second = createBatch();
        upload(second, csv, 200);
        validate(second).andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.counters[0].rowsByStatus.UNCHANGED").value(1));
        commit(second, "{}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from person", Long.class)).isEqualTo(people);
    }

    @Test
    void existingDocumentWithDifferentContentIsAConflict() throws Exception {
        String dni = uniqueDni();
        long first = createBatch();
        upload(first, HEADER + "\n" + studentRow(dni, "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);
        validate(first);
        commit(first, "{}");

        long second = createBatch();
        upload(second, HEADER + "\n" + studentRow(dni, "Otra", "Persona", "STUDENT", "1991-02-02", uniqueEmail(), ""), 200);
        validate(second).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.counters[0].rowsByStatus.CONFLICT").value(1));
    }

    @Test
    void fileWithUnknownColumnIsRejected() throws Exception {
        long batchId = createBatch();

        upload(batchId, HEADER + ",extra\n", 400, "IMPORT_FILE_INVALID_HEADER");
    }

    @Test
    void fileWithMissingColumnIsRejected() throws Exception {
        long batchId = createBatch();

        upload(batchId, "documentType,dni\nDNI,1\n", 400, "IMPORT_FILE_INVALID_HEADER");
    }

    @Test
    void commitIsRejectedBeforeValidation() throws Exception {
        long batchId = createBatch();
        upload(batchId, HEADER + "\n" + studentRow(uniqueDni(), "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);

        commit(batchId, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_STATE_INVALID"));
    }

    @Test
    void reuploadingAFileReplacesItsRowsAndRequiresValidationAgain() throws Exception {
        long batchId = createBatch();
        upload(batchId, HEADER + "\n" + studentRow(uniqueDni(), "Ana", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);
        validate(batchId);

        upload(batchId, HEADER + "\n" + studentRow(uniqueDni(), "Beto", "Paz", "STUDENT", "1990-01-01", uniqueEmail(), ""), 200);

        assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ?", Integer.class, batchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from import_batch where id = ?", String.class, batchId)).isEqualTo("FILES_UPLOADED");
    }

    @Test
    void onlyOneBatchCanBeCommittingPerInstitution() {
        long committing = jdbc.queryForObject("select count(*) from import_batch where status = 'COMMITTING'", Long.class);
        assertThat(committing).isZero();
        jdbc.update("insert into import_batch (name, status, institution_id, created_by_person_id, created_at) select 'a', 'COMMITTING', i.id, (select min(id) from person), now() from institution i limit 1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into import_batch (name, status, institution_id, created_by_person_id, created_at) select 'b', 'COMMITTING', i.id, (select min(id) from person), now() from institution i limit 1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void nonDirectorsCannotUseTheEndpoints() throws Exception {
        mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/data-imports").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/data-imports")).andExpect(status().isUnauthorized());
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Carga inicial\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void upload(long batchId, String csv, int expectedStatus) throws Exception {
        upload(batchId, csv, expectedStatus, null);
    }

    private void upload(long batchId, String csv, int expectedStatus, String expectedCode) throws Exception {
        var result = mockMvc.perform(multipart("/data-imports/" + batchId + "/files/PERSONS")
                .file(new MockMultipartFile("file", "01_personas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().is(expectedStatus));
        if (expectedCode != null) {
            result.andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    private org.springframework.test.web.servlet.ResultActions validate(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions commit(long batchId, String body) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Long> sideEffectCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        SIDE_EFFECT_TABLES.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class)));
        return counts;
    }

    private static String uniqueDni() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
    }

    private static String uniqueEmail() {
        return "import." + UUID.randomUUID() + "@example.com";
    }

    private static String studentRow(String dni, String first, String last, String role, String birth, String email,
            String registeredAt) {
        return "DNI," + dni + "," + first + "," + last + "," + role + ",F," + birth + "," + email
                + ",3512345678,Argentina,Av. Colón 1250,Córdoba,Córdoba,5000,," + registeredAt + ",ACTIVE,,,,,,,,,,,,\n";
    }

    private static String instructorRow(String dni, String email) {
        return "DNI," + dni + ",Marta,Ríos,INSTRUCTOR,F,1978-11-30," + email
                + ",3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n";
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("dataimport." + suffix);
        person.setEmail("dataimport." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
