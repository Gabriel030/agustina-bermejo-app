package com.red_escuela.backend.graduation.procedure;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedProcedureSources;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ApprovedProceduresIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private ApprovedProcedureSources approvedSources;
    @Autowired
    private RegularStudentCertificateRepository certificateRepository;

    private Long enrollmentId;
    private Long studentId;
    private Long sectionEnrollmentId;
    private int recordedDays;
    private String studentToken;
    private String strangerToken;
    private String staffToken;
    private String directorToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Person studentPerson = persistPerson("ALUMNO");
            Student student = persistStudent(studentPerson, institution);
            Student stranger = persistStudent(persistPerson("ALUMNO"), institution);
            Program program = fixtures.program();
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            SubjectSection section = fixtures.section(fixtures.subject(), fixtures.term(2026), StatusType.ACTIVE, null);
            section.setMaxAbsenceRate(Percentage.ofPoints(new BigDecimal("25")));
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(student);
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(section);
            sectionEnrollment.setStatus(EnrollmentStatus.ENROLLED);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(sectionEnrollment);
            entityManager.flush();
            enrollmentId = enrollment.getId();
            studentId = student.getId();
            sectionEnrollmentId = sectionEnrollment.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(stranger.getPerson());
            staffToken = jwtService.generateAccessToken(persistPerson("ADMINISTRATIVO"));
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
        });
        recordedDays = 0;
        recordAttendance(8, 1);
        setBillingMode("OFF");
    }

    @AfterEach
    void restoreBillingMode() {
        setBillingMode("FULL");
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    private Student persistStudent(Person person, Institution institution) {
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("regular." + suffix);
        person.setEmail("regular." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void recordAttendance(int present, int absent) {
        int attendanceDays = recordedDays;
        recordedDays += present + absent;
        transactions.executeWithoutResult(status -> {
            SubjectSectionEnrollment sectionEnrollment = entityManager.find(SubjectSectionEnrollment.class, sectionEnrollmentId);
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            var instructor = fixtures.instructor();
            for (int day = 0; day < present + absent; day++) {
                Attendance attendance = new Attendance();
                attendance.setProgramSubjectSectionEnrollment(sectionEnrollment);
                attendance.setClassDate(LocalDate.of(2026, 4, 1).plusDays(attendanceDays + day));
                attendance.setStatus(day < present ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT);
                attendance.setRecordedBy(instructor);
                entityManager.persist(attendance);
            }
        });
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long issueRegularCertificate() throws Exception {
        setBillingMode("FULL");
        mockMvc.perform(post("/me/regular-certificates").header("Authorization", bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + "}")).andExpect(status().isCreated());
        return certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId).get(0).getId();
    }

    private long downloadAudits(long certificateId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = 'DOCUMENT_PDF_DOWNLOADED' and entity_id = ?",
                Long.class, certificateId);
    }

    @Test
    void approvedListShowsOwnIssuedDocumentsAndNothingOfOtherStudents() throws Exception {
        long id = issueRegularCertificate();

        mockMvc.perform(get("/me/procedures/approved").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("REGULAR_STUDENT_CERTIFICATE"))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("VALID"))
                .andExpect(jsonPath("$[0].origin").value("SELF_SERVICE"))
                .andExpect(jsonPath("$[0].verifiable").value(true));
        mockMvc.perform(get("/me/procedures/approved").header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void approvedListShowsExpiredAndRevokedStates() throws Exception {
        long id = issueRegularCertificate();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 12, 31, 10, 0));
        mockMvc.perform(get("/me/procedures/approved").header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$[0].status").value("EXPIRED")).andExpect(jsonPath("$[0].verifiable").value(false));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        mockMvc.perform(post("/regular-certificates/" + id + "/revoke").header("Authorization", bearer(directorToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Se emitió por error de carga\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/me/procedures/approved").header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    @Test
    void redownloadReturnsTheSameDocumentAuditsItAndIsNotANewIssuance() throws Exception {
        long id = issueRegularCertificate();
        String code = certificateRepository.findById(id).orElseThrow().getCode();
        long auditsBefore = downloadAudits(id);

        byte[] first = mockMvc.perform(get("/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/" + id + "/pdf")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] second = mockMvc.perform(get("/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/" + id + "/pdf")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(second).isEqualTo(first);
        assertThat(downloadAudits(id)).isEqualTo(auditsBefore + 2);
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
        assertThat(certificateRepository.findById(id).orElseThrow().getCode()).isEqualTo(code);
    }

    @Test
    void anotherStudentGetsNotFoundWhenDownloadingSomeoneElsesDocument() throws Exception {
        long id = issueRegularCertificate();
        long auditsBefore = downloadAudits(id);

        mockMvc.perform(get("/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/" + id + "/pdf")
                .header("Authorization", bearer(strangerToken))).andExpect(status().isNotFound());

        assertThat(downloadAudits(id)).isEqualTo(auditsBefore);
    }

    @Test
    void pdfOfATypeWithoutIssuedDocumentsIsNotFound() throws Exception {
        mockMvc.perform(get("/me/procedures/approved/EXAM_CERTIFICATE/1/pdf").header("Authorization", bearer(studentToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void eligibilityOfATypeWithoutRegisteredStrategyAnswersUnavailable() throws Exception {
        mockMvc.perform(get("/me/procedures/APPROVED_SUBJECTS_CERTIFICATE/eligibility").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.summary").value("Este trámite todavía no está disponible"))
                .andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    void expiredDocumentIsListedButCannotBeRedownloaded() throws Exception {
        long id = issueRegularCertificate();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 12, 31, 10, 0));
        long auditsBefore = downloadAudits(id);

        mockMvc.perform(get("/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/" + id + "/pdf")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CERTIFICATE_EXPIRED"));

        assertThat(downloadAudits(id)).isEqualTo(auditsBefore);
    }

    @Test
    void everyIssuedDocumentTypeThatExistsTodayIsRegisteredAsASource() {
        assertThat(approvedSources.forType(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE)).isPresent();
        assertThat(approvedSources.forType(CertificateDocumentType.GRADUATION_CERTIFICATE)).isPresent();
    }

    @Test
    void replacedDocumentMarkedAsExpiredByARenewalCannotBeRedownloaded() throws Exception {
        long oldId = issueRegularCertificate();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 12, 31, 10, 0));
        mockMvc.perform(post("/me/regular-certificates").header("Authorization", bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + "}")).andExpect(status().isCreated());
        assertThat(certificateRepository.findById(oldId).orElseThrow().getStatus().name()).isEqualTo("EXPIRED");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        long auditsBefore = downloadAudits(oldId);

        mockMvc.perform(get("/me/procedures/approved/REGULAR_STUDENT_CERTIFICATE/" + oldId + "/pdf")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CERTIFICATE_EXPIRED"));

        assertThat(downloadAudits(oldId)).isEqualTo(auditsBefore);
    }
}
