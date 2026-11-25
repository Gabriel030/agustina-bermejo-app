package com.red_escuela.backend.graduation.certificate.regular;

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
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.template.PublishedTemplateFixtures;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OwnRegularDocumentIntegrationTest extends AbstractIntegrationTest {

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
    private RegularStudentCertificateRepository certificateRepository;
    @Autowired
    private RegularCertificateTemplate regularTemplate;
    @Autowired
    private CertificatePdfRenderer renderer;
    @Autowired
    private CertificateVerificationUrl verificationUrl;
    @Autowired
    private CertificateDigest digest;

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

    private void addOverdueInstallment() {
        transactions.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
        });
    }

    private long idOf(ResultActions result, String path) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return id.longValue();
    }

    private String textOf(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private ResultActions publicView(String code) throws Exception {
        return mockMvc.perform(get("/public/certificates/" + code).with(request -> {
            request.setRemoteAddr("10.9.8.7");
            return request;
        }));
    }

    private ResultActions generate(String token, String body) throws Exception {
        return mockMvc.perform(post("/me/documents/REGULAR_STUDENT_CERTIFICATE").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions eligibility(String token) throws Exception {
        return mockMvc.perform(get("/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility")
                .header("Authorization", "Bearer " + token));
    }

    private byte[] pdfBytesOf(long certificateId) throws Exception {
        return mockMvc.perform(get("/me/regular-certificates/" + certificateId + "/pdf")
                .header("Authorization", "Bearer " + studentToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private String pdfTextOf(long certificateId) throws Exception {
        byte[] pdf = mockMvc.perform(get("/me/regular-certificates/" + certificateId + "/pdf")
                .header("Authorization", "Bearer " + studentToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @Test
    void regularStudentGeneratesOwnCertificateWithoutApprovalWhenBillingIsOff() throws Exception {
        addOverdueInstallment();

        ResultActions result = generate(studentToken, "{}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("REGULAR_STUDENT_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("VALID"));
        long id = idOf(result, "$.id");
        String code = textOf(result, "$.code");

        assertThat(textOf(result, "$.verificationUrl")).endsWith("/verificar/" + code);
        assertThat(textOf(result, "$.expiresAt")).startsWith("2026-12-10");
        assertThat(jdbc.queryForObject("select standing_basis from regular_student_certificate where id = ?", String.class, id))
                .isEqualTo("BILLING_NOT_ENFORCED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'DOCUMENT_GENERATED_BY_STUDENT' and entity_id = ?",
                Integer.class, id)).isEqualTo(1);
        publicView(code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
        assertThat(pdfTextOf(id)).contains("CONSTANCIA DE ALUMNO REGULAR");
    }


    @Test
    void issuanceWithPublishedVersionStoresItsIdAndTemplateReference() throws Exception {
        long id = idOf(generate(studentToken, "{}").andExpect(status().isCreated()), "$.id");

        Map<String, Object> row = jdbc.queryForMap("select template_version_id, template_ref from regular_student_certificate where id = ?", id);
        assertThat(((Number) row.get("template_version_id")).longValue())
                .isEqualTo(PublishedTemplateFixtures.currentPublishedId(jdbc, "REGULAR_STUDENT_CERTIFICATE"));
        assertThat(row.get("template_ref")).isEqualTo(PublishedTemplateFixtures.currentPublishedRef(jdbc, "REGULAR_STUDENT_CERTIFICATE"));
    }

    @Test
    void republishingTheTemplateDoesNotChangeTheRegeneratedPdfNorItsHash() throws Exception {
        long id = idOf(generate(studentToken, "{}").andExpect(status().isCreated()), "$.id");
        String issuedSha = jdbc.queryForObject("select pdf_sha256 from regular_student_certificate where id = ?", String.class, id);
        PublishedTemplateFixtures.publishRebrandedVersion(jdbc, "REGULAR_STUDENT_CERTIFICATE");
        jdbc.update("update regular_student_certificate set pdf_path = 'missing/file.pdf' where id = ?", id);

        byte[] regenerated = pdfBytesOf(id);

        assertThat(digest.sha256(regenerated)).isEqualTo(issuedSha);
        assertThat(jdbc.queryForObject("select pdf_sha256 from regular_student_certificate where id = ?", String.class, id)).isEqualTo(issuedSha);
        try (var document = Loader.loadPDF(regenerated)) {
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("INSTITUTO REPUBLICADO");
        }
    }

    @Test
    void certificateWithoutTemplateVersionRegeneratesTheBuiltInDesignWithTheSameHash() throws Exception {
        long id = idOf(generate(studentToken, "{}").andExpect(status().isCreated()), "$.id");
        RegularStudentCertificate stored = certificateRepository.findById(id).orElseThrow();
        CertificateSnapshot builtIn = PublishedTemplateFixtures.withBuiltInRef(stored.getSnapshot(), RegularCertificateTemplate.REGULAR_BUILT_IN_V1);
        CertificateCode code = stored.certificateCode();
        byte[] legacy = renderer.render(regularTemplate.definitionFor(builtIn, stored.getExpiresAt().toLocalDate()), builtIn, code,
                verificationUrl.of(code));
        jdbc.update("update regular_student_certificate set template_version_id = null, template_ref = ?, pdf_sha256 = ?, "
                + "pdf_path = 'missing/file.pdf', snapshot = jsonb_set(snapshot - 'extraMarkers', '{templateRef}', to_jsonb(?::text)) where id = ?",
                RegularCertificateTemplate.REGULAR_BUILT_IN_V1, digest.sha256(legacy), RegularCertificateTemplate.REGULAR_BUILT_IN_V1, id);

        byte[] regenerated = pdfBytesOf(id);

        assertThat(regenerated).isEqualTo(legacy);
        assertThat(pdfTextOf(id)).contains("CONSTANCIA DE ALUMNO REGULAR", "es alumno/a regular de");
    }

    @Test
    void recipientIsStoredInTheSnapshotAndPrintedLiterallyInThePdf() throws Exception {
        long id = idOf(generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"OSDE {holder}\"}")
                .andExpect(status().isCreated()), "$.id");

        assertThat(jdbc.queryForObject("select snapshot ->> 'recipientLabel' from regular_student_certificate where id = ?",
                String.class, id)).isEqualTo("Se extiende a pedido del interesado para ser presentado ante: Obra social, OSDE {holder}");
        assertThat(pdfTextOf(id)).contains("presentado ante: Obra social, OSDE {holder}");
    }

    @Test
    void recipientTypeWithoutTextIsValidAndOtherWithoutTextOmitsTheBlock() throws Exception {
        long withType = idOf(generate(studentToken, "{\"recipientType\":\"EMPLOYER\"}").andExpect(status().isCreated()), "$.id");
        assertThat(jdbc.queryForObject("select snapshot ->> 'recipientLabel' from regular_student_certificate where id = ?",
                String.class, withType)).isEqualTo("Se extiende a pedido del interesado para ser presentado ante: Empleador");
        jdbc.update("update regular_student_certificate set status = 'REVOKED' where id = ?", withType);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        long withOther = idOf(generate(studentToken, "{\"recipientType\":\"OTHER\"}").andExpect(status().isCreated()), "$.id");

        assertThat(jdbc.queryForObject("select snapshot ->> 'recipientLabel' from regular_student_certificate where id = ?",
                String.class, withOther)).isNull();
    }

    @Test
    void otherRecipientPrintsOnlyTheName() throws Exception {
        long id = idOf(generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"Club Atlético\"}")
                .andExpect(status().isCreated()), "$.id");

        assertThat(jdbc.queryForObject("select snapshot ->> 'recipientLabel' from regular_student_certificate where id = ?",
                String.class, id)).isEqualTo("Se extiende a pedido del interesado para ser presentado ante: Club Atlético");
    }

    @Test
    void invalidRecipientIsRejectedWithoutIssuing() throws Exception {
        generate(studentToken, "{\"recipientType\":\"EMPLOYER\",\"recipientText\":\"Línea 1\\nLínea 2\"}")
                .andExpect(status().isBadRequest());
        generate(studentToken, "{\"recipientType\":\"EMPLOYER\",\"recipientText\":\"" + "x".repeat(81) + "\"}")
                .andExpect(status().isBadRequest());
        generate(studentToken, "{\"recipientText\":\"Sin tipo\"}").andExpect(status().isBadRequest());

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void secondGenerationOnTheSameDayHitsTheDailyLimitAndEligibilityExplainsIt() throws Exception {
        generate(studentToken, "{}").andExpect(status().isCreated());

        generate(studentToken, "{}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ISSUANCE_LIMIT_REACHED"));
        eligibility(studentToken).andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("ISSUANCE_LIMIT_REACHED"));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
    }

    @Test
    void eligibilityIsTrueForARegularStudent() throws Exception {
        eligibility(studentToken).andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.enrollmentId").value(enrollmentId)).andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    void nonRegularStudentIsNotEligibleAndCannotGenerate() throws Exception {
        recordAttendance(0, 40);

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NOT_REGULAR_STUDENT"));
        generate(studentToken, "{}").andExpect(status().isUnprocessableEntity());
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void pendingPaymentsBlockGenerationWhenBillingIsOnWithoutRevealingAmounts() throws Exception {
        setBillingMode("FULL");
        addOverdueInstallment();

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"));
        String body = generate(studentToken, "{}").andExpect(status().isUnprocessableEntity()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("15000").doesNotContain("15.000");
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void anotherStudentsEnrollmentIsNotFound() throws Exception {
        generate(strangerToken, "{\"enrollmentId\":" + enrollmentId + "}").andExpect(status().isNotFound());
        mockMvc.perform(get("/me/procedures/REGULAR_STUDENT_CERTIFICATE/eligibility").param("enrollmentId", enrollmentId.toString())
                .header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void theLimitResetsTheNextDayWhenThePreviousCertificateIsNoLongerValid() throws Exception {
        long first = idOf(generate(studentToken, "{}").andExpect(status().isCreated()), "$.id");
        jdbc.update("update regular_student_certificate set status = 'REVOKED' where id = ?", first);
        generate(studentToken, "{}").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ISSUANCE_LIMIT_REACHED"));

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 0, 5));

        generate(studentToken, "{}").andExpect(status().isCreated());
    }

    @Test
    void aValidCertificateFromAPreviousDayConflictsAndEligibilityPointsToDownloadingIt() throws Exception {
        generate(studentToken, "{}").andExpect(status().isCreated());
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 12, 10, 0));

        generate(studentToken, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGULAR_CERTIFICATE_ALREADY_ISSUED"));
        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("ISSUANCE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.reasons[0].message")
                        .value("Ya generaste esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites"));
    }

    @Test
    void mirrorBillingModeDoesNotAllowSelfIssueAndAsksToContactAdministration() throws Exception {
        setBillingMode("MIRROR");

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"))
                .andExpect(jsonPath("$.reasons[0].message").value("Comunicate con Administración"));
        generate(studentToken, "{}").andExpect(status().isUnprocessableEntity());
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void billingOffIgnoresDebtInEligibilityToo() throws Exception {
        addOverdueInstallment();

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    void ambiguousActiveProgramsAskToChooseOne() throws Exception {
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            ProgramEnrollment second = new ProgramEnrollment();
            second.setStudent(entityManager.find(ProgramEnrollment.class, enrollmentId).getStudent());
            second.setProgram(fixtures.program());
            second.setStatus(ProgramEnrollmentStatus.ENROLLED);
            second.setEnrollmentDate(LocalDateTime.of(2026, 3, 2, 10, 0));
            entityManager.persist(second);
        });

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NO_ACTIVE_ENROLLMENT"))
                .andExpect(jsonPath("$.reasons[0].message").value("Tenés más de una carrera activa: elegí para cuál querés la constancia"));
    }
}
