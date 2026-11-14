package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
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
class DataImportHistoryIT extends AbstractIntegrationTest {
    private static final String PERSON_HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone\n";
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
    private ProgramProgressReader progressReader;

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
    void importsCyclesSectionsEnrollmentsAndCoursesFeedingProgressAndClosureWithoutSideEffects() throws Exception {
        String program = "Tecnicatura " + suffix;
        String subject = "H" + suffix.substring(0, 3);
        String section = "SEC-" + suffix;
        String studentDni = uniqueDni();
        String teacherDni = uniqueDni();
        int year = 2015 + ThreadLocalRandom.current().nextInt(1, 5) ;
        Map<String, Long> before = sideEffectCounts();
        long batchId = createBatch();
        upload(batchId, "PERSONS", PERSON_HEADER
                + "DNI," + studentDni + ",Ana,Pérez,STUDENT,F,1990-01-01,ana." + suffix + "@example.com,3512345678,Argentina,Av. Colón 1,Córdoba,Córdoba,5000,," + year + "-03-01,ACTIVE,,,,,,,,,,,,\n"
                + "DNI," + teacherDni + ",Marta,Ríos,INSTRUCTOR,F,1978-11-30,marta." + suffix + "@example.com,3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n");
        upload(batchId, "PROGRAMS", "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n"
                + program + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n");
        upload(batchId, "SUBJECTS", "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n"
                + subject + ",Materia " + suffix + ",4,NUMERIC,3,ACTIVE\n");
        upload(batchId, "CURRICULUM", "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n"
                + program + "," + subject + ",1,FIRST,6,MANDATORY,PROMOTIONAL,1\n");
        upload(batchId, "TERMS", "year,termType,description,startDate,endDate,programName\n"
                + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + "," + program + "\n");
        upload(batchId, "SECTIONS", "sectionCode,subjectCode,year,termType,name,shift,instructorDni,closedOn,actaNumber,actaBook,actaFolio,programName\n"
                + section + "," + subject + "," + year + ",FIRST_SEMESTER,Comisión A,MORNING," + teacherDni + "," + year + "-07-20,,Libro 7,Folio 45,\n");
        upload(batchId, "PROGRAM_ENROLLMENTS", "documentType,dni,programName,enrollmentDate,status,completionDate,withdrawalDate,cohortYear\n"
                + "DNI," + studentDni + "," + program + "," + year + "-03-01,ENROLLED,,," + year + "\n");
        upload(batchId, "SECTION_ENROLLMENTS", "documentType,dni,programName,sectionCode,attemptNumber,status,finalScore,finalGradeType,finalDate\n"
                + "DNI," + studentDni + "," + program + "," + section + ",1,APPROVED,\"8,50\",PROMOTION," + year + "-07-20\n");

        mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));
        assertThat(jdbc.queryForObject("select count(*) from subject_section where code = ?", Integer.class, section)).isZero();
        assertThat(sideEffectCounts()).isEqualTo(before);

        mockMvc.perform(post("/data-imports/" + batchId + "/commit").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMMITTED"));

        assertThat(jdbc.queryForObject("select count(*) from program_enrollment where entry_year = ?", Integer.class, year)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from academic_offering_cycle c join program p on p.id = c.program_id where p.name = ? and c.status = 'CLOSED'",
                Integer.class, program)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select t.status from term t join subject_section s on s.term_id = t.id where s.code = ?",
                String.class, section)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from subject_section_instructor i join subject_section s on s.id = i.program_section_id where s.code = ?",
                Integer.class, section)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from commission_closure c join subject_section s on s.id = c.section_id where s.code = ? and c.status = 'CLOSED' and c.book = 'Libro 7' and c.report_number is not null",
                Integer.class, section)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from commission_closure_student cs join commission_closure c on c.id = cs.closure_id join subject_section s on s.id = c.section_id where s.code = ? and cs.final_condition = 'PROMOTES' and cs.promotion_grade = 8.50",
                Integer.class, section)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from subject_section_enrollment e join subject_section s on s.id = e.program_section_id where s.code = ? and e.status = 'APPROVED' and e.final_score_date = ?::date",
                Integer.class, section, year + "-07-20")).isEqualTo(1);
        Long programEnrollmentId = jdbc.queryForObject("select pe.id from program_enrollment pe join program p on p.id = pe.program_id where p.name = ?", Long.class, program);
        List<?> approved = transactions.execute(status -> progressReader.listApprovedSubjects(programEnrollmentId));
        assertThat(approved).hasSize(1);
        assertThat(sideEffectCounts()).isEqualTo(before);
    }

    @Test
    void sectionPointingToAnUnknownCycleAndEnrollmentWithoutStudentAreReported() throws Exception {
        long batchId = createBatch();
        upload(batchId, "SECTIONS", "sectionCode,subjectCode,year,termType,name,shift,instructorDni,closedOn,actaNumber,actaBook,actaFolio,programName\n"
                + "X-" + suffix + ",ZZZ9,2009,FIRST_SEMESTER,Comisión A,,,2009-07-20,,,,\n");
        upload(batchId, "SECTION_ENROLLMENTS", "documentType,dni,programName,sectionCode,attemptNumber,status,finalScore,finalGradeType,finalDate\n"
                + "DNI,12345678,Carrera " + suffix + ",X-" + suffix + ",1,APPROVED,,,\n");

        mockMvc.perform(post("/data-imports/" + batchId + "/validate").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForList("select jsonb_array_elements(errors)->>'code' from import_row where batch_id = ?",
                String.class, batchId)).contains("SUBJECT_NOT_FOUND", "TERM_NOT_FOUND", "PROGRAM_ENROLLMENT_NOT_FOUND",
                "FINAL_SCORE_REQUIRED", "FINAL_GRADE_TYPE_REQUIRED", "FINAL_DATE_REQUIRED");
    }

    private long createBatch() throws Exception {
        MvcResult result = mockMvc.perform(post("/data-imports").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Historial\"}"))
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
