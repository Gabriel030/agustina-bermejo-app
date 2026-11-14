package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
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
class DataImportStructureIT extends AbstractIntegrationTest {
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

    private String directorToken;
    private String suffix;

    @BeforeEach
    void prepare() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
        cleanImportTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
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
    void importsProgramSubjectsPlanAndPrerequisitesWithPriceAndWithoutSideEffects() throws Exception {
        String program = "Tecnicatura " + suffix;
        String codeA = "A" + suffix.substring(0, 3);
        String codeB = "B" + suffix.substring(0, 3);
        Map<String, Long> before = sideEffectCounts();
        long batchId = createBatch();
        upload(batchId, "PROGRAMS", "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n"
                + program + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + codeA + ",Materia A " + suffix + ",4,NUMERIC,3,ACTIVE\n" + codeB + ",Materia B " + suffix + ",4,NUMERIC,3,ACTIVE\n");
        upload(batchId, "CURRICULUM", "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n"
                + program + "," + codeA + ",1,FIRST,6,MANDATORY,PROMOTIONAL,1\n" + program + "," + codeB + ",1,SECOND,6,MANDATORY,PROMOTIONAL,2\n");
        upload(batchId, "PREREQUISITES", "programName,subjectCode,prerequisiteCode,requirementType,minGrade\n"
                + program + "," + codeB + "," + codeA + ",APPROVED,4\n");

        mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));
        mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));

        assertThat(jdbc.queryForObject("select count(*) from program where name = ?", Integer.class, program)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from program_subject ps join program p on p.id = ps.program_id where p.name = ?",
                Integer.class, program)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from program_subject_prerequisite", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from price pr join program p on p.id = pr.program_id where p.name = ?",
                Integer.class, program)).isEqualTo(2);
        assertThat(sideEffectCounts()).isEqualTo(before);
    }

    @Test
    void planEntryPointingToAnUnknownSubjectIsReportedAndCommitIsBlocked() throws Exception {
        long batchId = createBatch();
        upload(batchId, "CURRICULUM", "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n"
                + "Carrera inexistente " + suffix + ",ZZZ9,1,FIRST,6,MANDATORY,PROMOTIONAL,1\n");

        mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForList("select code from (select jsonb_array_elements(errors)->>'code' as code from import_row where batch_id = ?) e",
                String.class, batchId)).containsExactlyInAnyOrder("PROGRAM_NOT_FOUND", "SUBJECT_NOT_FOUND");
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Estructura\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void upload(long batchId, String type, String csv) throws Exception {
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/" + type)
                .file(new MockMultipartFile("file", type + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
    }

    private Map<String, Long> sideEffectCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        SIDE_EFFECT_TABLES.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class)));
        return counts;
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
