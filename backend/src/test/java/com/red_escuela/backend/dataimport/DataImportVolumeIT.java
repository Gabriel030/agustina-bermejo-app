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
import com.red_escuela.backend.testsupport.NightlyTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
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
class DataImportVolumeIT extends AbstractIntegrationTest {
    private static final List<String> SIDE_EFFECT_TABLES = List.of("notification", "notification_delivery",
            "event_publication", "event_publication_attempt", "event_publication_dead_letter", "invoice", "payment",
            "current_account_entry", "late_fee_charge", "chargeback", "graduation_certificate");
    private static final int VOLUME_STUDENTS = 10_000;
    private static final int VOLUME_SECTIONS = 20;
    private static final int STUDENTS_PER_ENROLLMENT_BATCH = 2_500;

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
    private int year;

    @BeforeEach
    void prepare() {
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
    void aBatchWithEveryEntityTypeCreatesNoNotificationsInvoicesAccountEntriesOrOutboxEvents() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 90_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZS", year, firstDni, 3, 2);
        String completedStudent = csv.studentDni(2);
        Map<String, Long> sideEffects = counts(SIDE_EFFECT_TABLES);
        long personsBefore = count("person");
        long batchId = createBatch();
        upload(batchId, "PERSONS", csv.persons());
        upload(batchId, "PROGRAMS", csv.programs());
        upload(batchId, "SUBJECTS", csv.subjects());
        upload(batchId, "CURRICULUM", csv.curriculum());
        upload(batchId, "PREREQUISITES", csv.prerequisites());
        upload(batchId, "TERMS", csv.terms());
        upload(batchId, "SECTIONS", csv.sections());
        upload(batchId, "PROGRAM_ENROLLMENTS", HistoricalDatasetCsv.PROGRAM_ENROLLMENT_HEADER
                + "DNI," + csv.studentDni(0) + "," + csv.program() + "," + year + "-03-01,ENROLLED,,,,\n"
                + "DNI," + csv.studentDni(1) + "," + csv.program() + "," + year + "-03-01,ENROLLED,,,,\n"
                + "DNI," + completedStudent + "," + csv.program() + "," + year + "-03-01,COMPLETED," + year + "-12-10,,,\n");
        upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(0, 2));
        upload(batchId, "GRADE_ITEMS", HistoricalDatasetCsv.GRADE_HEADER
                + "DNI," + csv.studentDni(0) + "," + csv.sectionCode(0) + ",EXAM,1," + year + "-05-12,8\n"
                + "DNI," + csv.studentDni(1) + "," + csv.sectionCode(1) + ",TP,1," + year + "-06-02,\"7,50\"\n");
        upload(batchId, "ATTENDANCES", HistoricalDatasetCsv.ATTENDANCE_HEADER
                + "DNI," + csv.studentDni(0) + "," + csv.sectionCode(0) + "," + year + "-03-18,PRESENT\n"
                + "DNI," + csv.studentDni(1) + "," + csv.sectionCode(1) + "," + year + "-03-25,ABSENT\n");
        upload(batchId, "GRADUATIONS", HistoricalDatasetCsv.GRADUATION_HEADER
                + "DNI," + completedStudent + "," + csv.program() + "," + year + "-12-10,REG-" + suffix + ",T-1\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));

        assertThat(count("person")).isEqualTo(personsBefore + 4);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Integer.class, batchId)).isGreaterThan(20);
        assertThat(jdbc.queryForObject("select count(distinct entity_type) from import_created_entity where batch_id = ?", Integer.class, batchId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from graduation_request where registry_number = ?", Integer.class, "REG-" + suffix)).isEqualTo(1);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        assertThat(count("person")).isEqualTo(personsBefore);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);
    }

    @NightlyTest(reason = "Importa 10.000 personas y 200.000 cursadas por HTTP real y mide tiempo y memoria; no se puede partir sin perder la medición de volumen")
    @Test
    void tenThousandPeopleAndTwoHundredThousandSectionEnrollmentsImportWithoutSideEffectsAndRollBack() throws Exception {
        long firstDni = ThreadLocalRandom.current().nextLong(10_000_000L, 60_000_000L);
        HistoricalDatasetCsv csv = new HistoricalDatasetCsv(suffix, "ZV", year, firstDni, VOLUME_STUDENTS, VOLUME_SECTIONS);
        Map<String, Long> sideEffects = counts(SIDE_EFFECT_TABLES);
        long personsBefore = count("person");
        long sectionEnrollmentsBefore = count("subject_section_enrollment");
        PeakHeap heap = PeakHeap.start();
        List<Long> batches = new ArrayList<>();

        long baseBatch = createBatch();
        batches.add(baseBatch);
        timed("base upload", () -> {
            upload(baseBatch, "PERSONS", csv.persons());
            upload(baseBatch, "PROGRAMS", csv.programs());
            upload(baseBatch, "SUBJECTS", csv.subjects());
            upload(baseBatch, "CURRICULUM", csv.curriculum());
            upload(baseBatch, "TERMS", csv.terms());
            upload(baseBatch, "SECTIONS", csv.sections());
            upload(baseBatch, "PROGRAM_ENROLLMENTS", csv.programEnrollments());
        });
        timed("base validate", () -> validate(baseBatch).andExpect(jsonPath("$.status").value("VALIDATED")));
        timed("base commit", () -> commit(baseBatch).andExpect(jsonPath("$.status").value("COMMITTED")));
        assertThat(count("person")).isEqualTo(personsBefore + VOLUME_STUDENTS + 1);

        for (int first = 0; first < VOLUME_STUDENTS; first += STUDENTS_PER_ENROLLMENT_BATCH) {
            int from = first;
            long batchId = createBatch();
            batches.add(batchId);
            String label = "section enrollments from student " + from;
            timed(label + " upload", () -> upload(batchId, "SECTION_ENROLLMENTS", csv.sectionEnrollments(from, STUDENTS_PER_ENROLLMENT_BATCH)));
            timed(label + " validate", () -> validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED")));
            timed(label + " commit", () -> commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED")));
        }

        assertThat(count("subject_section_enrollment")).isEqualTo(sectionEnrollmentsBefore + (long) VOLUME_STUDENTS * VOLUME_SECTIONS);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);

        for (int i = batches.size() - 1; i >= 0; i--) {
            long batchId = batches.get(i);
            timed("rollback batch " + batchId, () -> postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ROLLED_BACK")));
        }
        assertThat(count("person")).isEqualTo(personsBefore);
        assertThat(count("subject_section_enrollment")).isEqualTo(sectionEnrollmentsBefore);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);
        System.out.println("VOLUME peak heap MB: " + heap.stopAndReadMegabytes());
    }

    private interface Step {
        void run() throws Exception;
    }

    private static void timed(String label, Step step) throws Exception {
        long start = System.nanoTime();
        step.run();
        System.out.println("VOLUME " + label + ": " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static final class PeakHeap {
        private final AtomicLong peakBytes = new AtomicLong();
        private final Thread sampler;

        private PeakHeap() {
            sampler = new Thread(() -> {
                Runtime runtime = Runtime.getRuntime();
                while (!Thread.currentThread().isInterrupted()) {
                    peakBytes.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), Math::max);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            sampler.setDaemon(true);
        }

        static PeakHeap start() {
            PeakHeap heap = new PeakHeap();
            heap.sampler.start();
            return heap;
        }

        long stopAndReadMegabytes() {
            sampler.interrupt();
            return peakBytes.get() / (1024 * 1024);
        }
    }

    private ResultActions validate(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk());
    }

    private ResultActions commit(long batchId) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    private ResultActions postWithConfirmation(long batchId, String action, String word) throws Exception {
        return mockMvc.perform(post("/data-imports/" + batchId + "/" + action).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"" + word + "\"}"));
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Volumen\"}"))
                .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void upload(long batchId, String type, String csv) throws Exception {
        mockMvc.perform(multipart("/data-imports/" + batchId + "/files/" + type)
                .file(new MockMultipartFile("file", type + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk());
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private Map<String, Long> counts(List<String> tables) {
        Map<String, Long> counts = new TreeMap<>();
        tables.forEach(table -> counts.put(table, count(table)));
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
