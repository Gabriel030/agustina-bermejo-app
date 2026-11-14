package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.api.EnrollmentAbsenceCountsView;
import com.red_escuela.backend.attendance.api.SectionAbsenceCountsReader;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.api.GradeItemFinder;
import com.red_escuela.backend.evaluation.api.GradeItemView;
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
class DataImportGradesAttendanceIT extends AbstractIntegrationTest {
    private static final String PERSON_HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone\n";
    private static final String GRADE_HEADER = "documentType,dni,sectionCode,type,sequenceNumber,date,score\n";
    private static final String ATTENDANCE_HEADER = "documentType,dni,sectionCode,classDate,status\n";
    private static final List<String> SIDE_EFFECT_TABLES = List.of("notification", "notification_delivery",
            "event_publication", "invoice", "payment", "current_account_entry");
    private static final List<String> HISTORY_TABLES = List.of("grade_item", "section_grade_item_publication", "attendance",
            "class_session", "grade_item_audit", "attendance_correction");

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
    private GradeItemFinder gradeItemFinder;
    @Autowired
    private SectionAbsenceCountsReader absenceCountsReader;
    @Autowired
    private AttendanceStandingReader standingReader;

    private String directorToken;
    private String suffix;
    private String program;
    private String subject;
    private String section;
    private String studentDni;
    private String teacherDni;
    private int year;

    @BeforeEach
    void prepare() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
        cleanImportTables();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        program = "Tecnicatura " + suffix;
        subject = "G" + suffix.substring(0, 3);
        section = "SEC-" + suffix;
        studentDni = uniqueDni();
        teacherDni = uniqueDni();
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
    void importedGradesAndAttendanceFeedTheRealReadersAndRollBackCleanly() throws Exception {
        Map<String, Long> sideEffects = counts(SIDE_EFFECT_TABLES);
        Map<String, Long> historyBefore = counts(HISTORY_TABLES);
        long batchId = createBatch();
        uploadStructure(batchId, teacherDni);
        upload(batchId, "GRADE_ITEMS", GRADE_HEADER
                + "DNI," + studentDni + "," + section + ",EXAM,1," + year + "-05-12,8\n"
                + "DNI," + studentDni + "," + section + ",TP,1," + year + "-06-02,\"7,50\"\n");
        upload(batchId, "ATTENDANCES", ATTENDANCE_HEADER
                + "DNI," + studentDni + "," + section + "," + year + "-03-18,PRESENT\n"
                + "DNI," + studentDni + "," + section + "," + year + "-03-25,ABSENT\n"
                + "DNI," + studentDni + "," + section + "," + year + "-04-01,LATE\n"
                + "DNI," + studentDni + "," + section + "," + year + "-04-08,JUSTIFIED\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATED"));
        assertThat(counts(HISTORY_TABLES)).isEqualTo(historyBefore);
        commit(batchId).andExpect(jsonPath("$.status").value("COMMITTED"));

        Long enrollmentId = jdbc.queryForObject("select e.id from subject_section_enrollment e join subject_section s on s.id = e.program_section_id where s.code = ?", Long.class, section);
        List<GradeItemView> published = transactions.execute(status -> gradeItemFinder.findPublishedByProgramEnrollment(enrollmentId));
        assertThat(published).hasSize(2);
        EnrollmentAbsenceCountsView counts = transactions.execute(status -> absenceCountsReader.countByProgramEnrollments(List.of(enrollmentId)).get(enrollmentId));
        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.absent()).isEqualTo(1);
        assertThat(counts.pendingJustification()).isZero();
        assertThat(transactions.execute(status -> standingReader.readProgramEnrollmentStanding(enrollmentId)).presentClasses()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from class_session cs join subject_section s on s.id = cs.program_section_id where s.code = ? and cs.status = 'HELD'", Integer.class, section)).isEqualTo(4);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);

        postWithConfirmation(batchId, "rollback", "DESHACER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        assertThat(counts(HISTORY_TABLES)).isEqualTo(historyBefore);
        assertThat(counts(SIDE_EFFECT_TABLES)).isEqualTo(sideEffects);
        assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ? and entity_type in ('GRADE_ITEMS', 'ATTENDANCES')", Integer.class, batchId)).isZero();
    }

    @Test
    void referencesToSectionEnrollmentsAlreadyInTheSystemAreResolvedAndExistingRowsAreConflicts() throws Exception {
        long first = createBatch();
        uploadStructure(first, teacherDni);
        upload(first, "GRADE_ITEMS", GRADE_HEADER + "DNI," + studentDni + "," + section + ",EXAM,1," + year + "-05-12,8\n");
        upload(first, "ATTENDANCES", ATTENDANCE_HEADER + "DNI," + studentDni + "," + section + "," + year + "-03-18,PRESENT\n");
        validate(first);
        commit(first).andExpect(jsonPath("$.status").value("COMMITTED"));

        long second = createBatch();
        upload(second, "GRADE_ITEMS", GRADE_HEADER
                + "DNI," + studentDni + "," + section + ",EXAM,1," + year + "-05-12,9\n"
                + "DNI," + studentDni + "," + section + ",EXAM,2," + year + "-07-01,9\n");
        upload(second, "ATTENDANCES", ATTENDANCE_HEADER
                + "DNI," + studentDni + "," + section + "," + year + "-03-18,ABSENT\n"
                + "DNI," + studentDni + "," + section + "," + year + "-03-25,ABSENT\n");
        validate(second);
        long third = createBatch();
        upload(third, "GRADE_ITEMS", GRADE_HEADER + "DNI," + studentDni + "," + section + ",EXAM,1," + year + "-05-12,8\n");
        validate(third);
        assertThat(rowStatuses(third, "GRADE_ITEMS")).containsExactly("UNCHANGED");

        assertThat(rowStatuses(second, "GRADE_ITEMS")).containsExactly("CONFLICT", "VALID");
        assertThat(rowStatuses(second, "ATTENDANCES")).containsExactly("CONFLICT", "VALID");
    }

    @Test
    void invalidGradesAndAttendanceAreReportedPerRow() throws Exception {
        long batchId = createBatch();
        uploadStructure(batchId, "");
        upload(batchId, "GRADE_ITEMS", GRADE_HEADER
                + "DNI," + studentDni + "," + section + ",FINAL,1," + year + "-05-12,8\n"
                + "DNI," + studentDni + "," + section + ",EXAM,2," + year + "-05-12,11\n"
                + "DNI," + studentDni + "," + section + ",EXAM,3,2999-05-12,8\n"
                + "DNI," + uniqueDni() + "," + section + ",EXAM,4," + year + "-05-12,8\n"
                + "DNI," + studentDni + "," + section + ",EXAM,5," + year + "-05-12,8\n"
                + "DNI," + studentDni + "," + section + ",EXAM,5," + year + "-05-13,9\n");
        upload(batchId, "ATTENDANCES", ATTENDANCE_HEADER
                + "DNI," + studentDni + "," + section + "," + year + "-03-18,JUSTIFICATION_PENDING\n"
                + "DNI," + studentDni + "," + section + "," + year + "-03-25,PRESENT\n"
                + "DNI," + studentDni + "," + section + "," + year + "-03-25,ABSENT\n"
                + "DNI," + uniqueDni() + "," + section + "," + year + "-03-26,PRESENT\n");

        validate(batchId).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));

        assertThat(rowCodes(batchId, "GRADE_ITEMS")).contains("GRADE_ITEM_TYPE_NOT_ACCEPTED", "SCORE_OUT_OF_RANGE",
                "DATE_IN_FUTURE", "SECTION_ENROLLMENT_NOT_FOUND", "GRADEITEM_DUPLICATED_IN_FILE");
        assertThat(rowCodes(batchId, "ATTENDANCES")).contains("STATUS_NOT_ACCEPTED", "SECTION_WITHOUT_INSTRUCTOR",
                "ATTENDANCEDAY_DUPLICATED_IN_FILE", "SECTION_ENROLLMENT_NOT_FOUND");
    }

    private void uploadStructure(long batchId, String instructorDni) throws Exception {
        String persons = PERSON_HEADER
                + "DNI," + studentDni + ",Ana,Pérez,STUDENT,F,1990-01-01,ana." + suffix + "@example.com,3512345678,Argentina,Av. Colón 1,Córdoba,Córdoba,5000,," + year + "-03-01,ACTIVE,,,,,,,,,,,,\n";
        if (!instructorDni.isEmpty()) {
            persons += "DNI," + instructorDni + ",Marta,Ríos,INSTRUCTOR,F,1978-11-30,marta." + suffix + "@example.com,3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n";
        }
        upload(batchId, "PERSONS", persons);
        upload(batchId, "PROGRAMS", "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n"
                + program + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + subject + ",Materia " + suffix + ",4,NUMERIC,3,ACTIVE\n");
        upload(batchId, "CURRICULUM", "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n"
                + program + "," + subject + ",1,FIRST,6,MANDATORY,PROMOTIONAL,1\n");
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + "," + program + "\n");
        upload(batchId, "SECTIONS", "sectionCode,subjectCode,year,termType,name,shift,instructorDni,closedOn,actaNumber,actaBook,actaFolio,programName\n"
                + section + "," + subject + "," + year + ",FIRST_SEMESTER,Comisión A,MORNING," + instructorDni + "," + year + "-07-20,,Libro 7,Folio 45,\n");
        upload(batchId, "PROGRAM_ENROLLMENTS", "documentType,dni,programName,enrollmentDate,status,completionDate,withdrawalDate,cohortYear\n"
                + "DNI," + studentDni + "," + program + "," + year + "-03-01,ENROLLED,,," + year + "\n");
        upload(batchId, "SECTION_ENROLLMENTS", "documentType,dni,programName,sectionCode,attemptNumber,status,finalScore,finalGradeType,finalDate\n"
                + "DNI," + studentDni + "," + program + "," + section + ",1,APPROVED,\"8,50\",PROMOTION," + year + "-07-20\n");
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Notas y asistencia\"}"))
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
        tables.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class)));
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
