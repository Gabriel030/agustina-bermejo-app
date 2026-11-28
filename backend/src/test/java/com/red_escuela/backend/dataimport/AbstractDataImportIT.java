package com.red_escuela.backend.dataimport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
abstract class AbstractDataImportIT extends AbstractIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected TransactionTemplate transactions;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    protected Long directorId;
    protected String suffix;
    protected int year;

    @BeforeEach
    void prepareImportEnvironment() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
        cleanImportTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        year = 2015 + ThreadLocalRandom.current().nextInt(1, 5);
        transactions.executeWithoutResult(status -> {
            if (jdbc.queryForObject("select count(*) from institution", Integer.class) == 0) {
                Institution institution = new Institution();
                institution.setName("Instituto de prueba");
                entityManager.persist(institution);
                entityManager.flush();
            }
            directorId = persistDirector().getId();
        });
    }

    @AfterEach
    void restoreImportEnvironment() {
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

    protected String directorToken() {
        return transactions.execute(status -> jwtService.generateAccessToken(entityManager.find(Person.class, directorId)));
    }

    protected ResultActions validate(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken()))
                .andExpect(status().isOk());
    }

    protected ResultActions commit(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    protected ResultActions rollback(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/rollback").header("Authorization", "Bearer " + directorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"DESHACER\"}"));
    }

    protected long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Precarga\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    protected void upload(long batchId, String type, String csv) throws Exception {
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/" + type)
                .file(new MockMultipartFile("file", type + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + directorToken())).andExpect(status().isOk());
    }

    protected void importBaseStructure(long batchId, HistoricalDatasetCsv csv) throws Exception {
        importBaseStructure(batchId, csv, csv.programEnrollments());
    }

    protected void importBaseStructure(long batchId, HistoricalDatasetCsv csv, String programEnrollmentsCsv) throws Exception {
        importBaseStructure(batchId, csv, programEnrollmentsCsv, csv.termsWithAnnualCycle());
    }

    protected void importBaseStructure(long batchId, HistoricalDatasetCsv csv, String programEnrollmentsCsv, String termsCsv) throws Exception {
        upload(batchId, "PERSONS", csv.persons());
        upload(batchId, "PROGRAMS", csv.programs());
        upload(batchId, "SUBJECTS", csv.subjects());
        upload(batchId, "CURRICULUM", csv.curriculum());
        upload(batchId, "TERMS", termsCsv);
        upload(batchId, "SECTIONS", csv.sections());
        upload(batchId, "PROGRAM_ENROLLMENTS", programEnrollmentsCsv);
        validate(batchId);
        commit(batchId);
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
