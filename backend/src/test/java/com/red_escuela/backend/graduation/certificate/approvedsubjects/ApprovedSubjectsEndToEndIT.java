package com.red_escuela.backend.graduation.certificate.approvedsubjects;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ApprovedSubjectsEndToEndIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final AtomicInteger CLIENTS = new AtomicInteger(0);

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
    private CertificateDigest digest;

    private Long enrollmentId;
    private Long studentId;
    private String studentToken;
    private String strangerToken;
    private String directorToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(NOW);
        deleteCertificates();
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
            Subject approvedSubject = fixtures.subject();
            ProgramSubject entry = new ProgramSubject();
            entry.setProgram(program);
            entry.setSubject(approvedSubject);
            entry.setCredits(4);
            entry.setStatus(StatusType.ACTIVE);
            entityManager.persist(entry);
            SubjectEquivalence equivalence = new SubjectEquivalence();
            equivalence.setProgramEnrollment(enrollment);
            equivalence.setSubjectId(approvedSubject.getId());
            equivalence.setOriginInstitution("Otra institución");
            equivalence.setOriginSubjectName("Materia de origen");
            equivalence.setOriginScore(Grade.of(new BigDecimal("8")));
            equivalence.setRecognizedOn(LocalDate.of(2026, 5, 20));
            equivalence.setResolutionNumber("R-1");
            equivalence.setReason("Equivalencia de prueba");
            equivalence.setRecordedById(studentPerson.getId());
            equivalence.setRecordedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
            entityManager.persist(equivalence);
            var instructor = fixtures.instructor();
            for (int day = 0; day < 9; day++) {
                Attendance attendance = new Attendance();
                attendance.setProgramSubjectSectionEnrollment(sectionEnrollment);
                attendance.setClassDate(LocalDate.of(2026, 4, 1).plusDays(day));
                attendance.setStatus(day < 8 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT);
                attendance.setRecordedBy(instructor);
                entityManager.persist(attendance);
            }
            entityManager.flush();
            enrollmentId = enrollment.getId();
            studentId = student.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(stranger.getPerson());
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
        });
        setBillingMode("OFF");
    }

    @AfterEach
    void cleanUp() {
        setBillingMode("FULL");
        deleteCertificates();
        MutableClock.INSTANCE.reset();
    }

    private void deleteCertificates() {
        jdbc.update("delete from verifiable_document where document_type = 'APPROVED_SUBJECTS_CERTIFICATE'");
        jdbc.update("delete from approved_subjects_certificate");
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
        person.setUsername("approved." + suffix);
        person.setEmail("approved." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private MvcResult request(String token) throws Exception {
        return mockMvc.perform(post("/me/approved-subjects-certificates").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enrollmentId\":" + enrollmentId + "}")).andReturn();
    }

    private MvcResult requestOk() throws Exception {
        MvcResult result = request(studentToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return result;
    }

    private String verificationStatusOf(String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/public/documents/{code}", code).with(from("203.0.113." + CLIENTS.incrementAndGet()))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.status");
    }

    private byte[] downloadOwn(long id, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/me/procedures/approved/APPROVED_SUBJECTS_CERTIFICATE/" + id + "/pdf")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private String storedHash(long id) {
        return jdbc.queryForObject("select pdf_sha256 from approved_subjects_certificate where id = ?", String.class, id);
    }

    private long auditCount(String action, long id) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Long.class, action, id);
    }

    @Test
    void issuedCertificateIsDownloadedWithItsStoredHashAndVerifiedPublicly() throws Exception {
        MvcResult issued = requestOk();
        long id = ((Number) JsonPath.read(issued.getResponse().getContentAsString(), "$.id")).longValue();
        String code = JsonPath.read(issued.getResponse().getContentAsString(), "$.code");

        byte[] first = downloadOwn(id, studentToken);
        byte[] second = downloadOwn(id, studentToken);

        assertThat(digest.sha256(first)).isEqualTo(storedHash(id)).isEqualTo(digest.sha256(second));
        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", id)).isEqualTo(2L);
        assertThat(verificationStatusOf(code)).isEqualTo("VALID");
    }

    @Test
    void dailyLimitStopsASecondRequestAndKeepsTheFirstCertificateDownloadable() throws Exception {
        long id = ((Number) JsonPath.read(requestOk().getResponse().getContentAsString(), "$.id")).longValue();

        MvcResult second = request(studentToken);

        assertThat(second.getResponse().getStatus()).isEqualTo(429);
        assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.code")).isEqualTo("ISSUANCE_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("select count(*) from approved_subjects_certificate", Long.class)).isEqualTo(1L);
        assertThat(downloadOwn(id, studentToken)).isNotEmpty();
    }

    @Test
    void revocationBlocksTheDownloadAndTheVerificationReportsRevoked() throws Exception {
        MvcResult issued = requestOk();
        long id = ((Number) JsonPath.read(issued.getResponse().getContentAsString(), "$.id")).longValue();
        String code = JsonPath.read(issued.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(post("/approved-subjects-certificates/" + id + "/revoke").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo interno suficiente\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/me/procedures/approved/APPROVED_SUBJECTS_CERTIFICATE/" + id + "/pdf")
                .header("Authorization", "Bearer " + studentToken)).andExpect(status().isNotFound());
        assertThat(verificationStatusOf(code)).isEqualTo("REVOKED");
        assertThat(auditCount("APPROVED_SUBJECTS_CERT_REVOKED", id)).isEqualTo(1L);
    }

    @Test
    void certificateExpiresAtTheExactInstantOfItsExpiry() throws Exception {
        MvcResult issued = requestOk();
        long id = ((Number) JsonPath.read(issued.getResponse().getContentAsString(), "$.id")).longValue();
        String code = JsonPath.read(issued.getResponse().getContentAsString(), "$.code");
        LocalDateTime expiresAt = jdbc.queryForObject("select expires_at from approved_subjects_certificate where id = ?", Timestamp.class, id)
                .toLocalDateTime();

        MutableClock.INSTANCE.setDateTime(expiresAt.minusMinutes(1));
        assertThat(verificationStatusOf(code)).isEqualTo("VALID");
        assertThat(downloadOwn(id, studentToken)).isNotEmpty();

        MutableClock.INSTANCE.setDateTime(expiresAt);
        assertThat(verificationStatusOf(code)).isEqualTo("EXPIRED");
        mockMvc.perform(get("/me/procedures/approved/APPROVED_SUBJECTS_CERTIFICATE/" + id + "/pdf")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CERTIFICATE_EXPIRED"));
    }

    @Test
    void anotherStudentCannotRequestDownloadOrRevokeTheCertificate() throws Exception {
        long id = ((Number) JsonPath.read(requestOk().getResponse().getContentAsString(), "$.id")).longValue();

        assertThat(request(strangerToken).getResponse().getStatus()).isEqualTo(404);
        mockMvc.perform(get("/me/procedures/approved/APPROVED_SUBJECTS_CERTIFICATE/" + id + "/pdf")
                .header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
        mockMvc.perform(post("/approved-subjects-certificates/" + id + "/revoke").header("Authorization", "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo suficiente\"}")).andExpect(status().isForbidden());
        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", id)).isZero();
        assertThat(jdbc.queryForObject("select status from approved_subjects_certificate where id = ?", String.class, id)).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("select count(*) from approved_subjects_certificate where student_id = ?", Long.class, studentId)).isEqualTo(1L);
    }

    @Test
    void verificationResponseExposesNeitherSubjectsNorGradesNorTheRevocationReason() throws Exception {
        MvcResult issued = requestOk();
        long id = ((Number) JsonPath.read(issued.getResponse().getContentAsString(), "$.id")).longValue();
        String code = JsonPath.read(issued.getResponse().getContentAsString(), "$.code");
        mockMvc.perform(post("/approved-subjects-certificates/" + id + "/revoke").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo interno suficiente\"}")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/public/documents/{code}", code).with(from("203.0.113." + CLIENTS.incrementAndGet())))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"documentType\":\"APPROVED_SUBJECTS_CERTIFICATE\"", "\"status\":\"REVOKED\"")
                .doesNotContain("Motivo interno", "Materia de origen", "8.00");
    }
}
