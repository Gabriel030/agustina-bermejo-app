package com.red_escuela.backend.graduation.certificate.examregistration;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ExamRegistrationIssuanceIT extends AbstractIntegrationTest {

    private static final String PATH = "/me/documents/EXAM_REGISTRATION_CERTIFICATE";

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

    private Long enrollmentId;
    private Long registrationId;
    private Long boardId;
    private Long sectionEnrollmentId;
    private Long subjectId;
    private Long termId;
    private String studentToken;
    private String strangerToken;

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
            Person strangerPerson = persistPerson("ALUMNO");
            persistStudent(strangerPerson, institution);
            Program program = fixtures.program();
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            Subject subject = fixtures.subject();
            Term term = fixtures.term(2026);
            SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, null);
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(student);
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(section);
            sectionEnrollment.setStatus(EnrollmentStatus.REGULAR);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            sectionEnrollment.setAttemptNumber(1);
            entityManager.persist(sectionEnrollment);
            ExamBoard board = new ExamBoard();
            board.setSubject(subject);
            board.setTerm(term);
            board.setExamDate(LocalDateTime.of(2026, 11, 20, 9, 0));
            board.setStatus(ExamBoardStatus.SCHEDULED);
            entityManager.persist(board);
            ExamBoardRegistration registration = new ExamBoardRegistration();
            registration.setExamBoard(board);
            registration.setProgramSubjectSectionEnrollment(sectionEnrollment);
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.of(2026, 11, 1, 10, 0));
            entityManager.persist(registration);
            entityManager.flush();
            enrollmentId = enrollment.getId();
            registrationId = registration.getId();
            boardId = board.getId();
            sectionEnrollmentId = sectionEnrollment.getId();
            subjectId = subject.getId();
            termId = term.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(strangerPerson);
        });
        jdbc.update("delete from exam_registration_certificate");
        setBillingMode("OFF");
    }

    @AfterEach
    void restoreBillingMode() {
        jdbc.update("delete from exam_registration_certificate");
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
        person.setUsername("examreg." + suffix);
        person.setEmail("examreg." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void addOverdueInstallment() {
        transactions.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
        });
    }

    private Long secondRegistrationOn(LocalDateTime examDate) {
        return transactions.execute(status -> {
            ExamBoard board = new ExamBoard();
            board.setSubject(entityManager.find(Subject.class, subjectId));
            board.setTerm(entityManager.find(Term.class, termId));
            board.setExamDate(examDate);
            board.setStatus(ExamBoardStatus.SCHEDULED);
            entityManager.persist(board);
            ExamBoardRegistration registration = new ExamBoardRegistration();
            registration.setExamBoard(board);
            registration.setProgramSubjectSectionEnrollment(entityManager.find(SubjectSectionEnrollment.class, sectionEnrollmentId));
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.of(2026, 11, 2, 10, 0));
            entityManager.persist(registration);
            entityManager.flush();
            return registration.getId();
        });
    }

    @Value("${app.certificates.directory}")
    private String certificatesDirectory;

    private static RequestPostProcessor clientAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private ResultActions generate(String token, String json) throws Exception {
        var request = post(PATH).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        return mockMvc.perform(json == null ? request : request.content(json));
    }

    private ResultActions download(String token, Long id) throws Exception {
        return mockMvc.perform(get("/me/procedures/approved/EXAM_REGISTRATION_CERTIFICATE/{id}/pdf", id)
                .header("Authorization", "Bearer " + token));
    }

    private long certificateCount() {
        return jdbc.queryForObject("select count(*) from exam_registration_certificate", Long.class);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private Long issuedId(String token) throws Exception {
        MvcResult result = generate(token, "{}").andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void generationPersistsSnapshotHashEngineVersionAndTemplate() throws Exception {
        generate(studentToken, "{\"registrationId\":" + registrationId + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("EXAM_REGISTRATION_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T00:00:00"))
                .andExpect(jsonPath("$.verificationUrl").value(org.hamcrest.Matchers.containsString("/verificar/")));

        Map<String, Object> row = jdbc.queryForMap("select * from exam_registration_certificate");
        assertThat(row.get("pdf_sha256").toString()).hasSize(64);
        assertThat(row.get("render_engine_version")).isEqualTo("1");
        assertThat(row.get("template_ref")).isEqualTo("EXAM_REGISTRATION_BUILT_IN_V1");
        assertThat(row.get("snapshot").toString()).contains("\"titleName\"");
        assertThat(row.get("recipient_type")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_GENERATED_BY_STUDENT' and entity_id in (select id from exam_registration_certificate)", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void generationWithoutBodyUsesTheDefaultRegistration() throws Exception {
        generate(studentToken, null).andExpect(status().isCreated());

        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void secondGenerationForTheSameRegistrationReturnsTheSameDocumentWithoutCountingTowardsTheLimit() throws Exception {
        Long first = issuedId(studentToken);
        Long second = issuedId(studentToken);

        assertThat(second).isEqualTo(first);
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void secondRegistrationOnTheSameDayHitsTheDailyLimit() throws Exception {
        issuedId(studentToken);
        Long other = secondRegistrationOn(LocalDateTime.of(2026, 11, 25, 9, 0));

        generate(studentToken, "{\"registrationId\":" + other + "}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ISSUANCE_LIMIT_REACHED"));
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void limitResetsTheNextDay() throws Exception {
        issuedId(studentToken);
        Long other = secondRegistrationOn(LocalDateTime.of(2026, 11, 25, 9, 0));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        generate(studentToken, "{\"registrationId\":" + other + "}").andExpect(status().isCreated());
        assertThat(certificateCount()).isEqualTo(2L);
    }

    @Test
    void recipientIsStoredLiterally() throws Exception {
        generate(studentToken, "{\"recipientType\":\"EMPLOYER\",\"recipientText\":\"  Acme S.A. - RRHH ñ  \"}")
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap("select recipient_type, recipient_text from exam_registration_certificate");
        assertThat(row.get("recipient_type")).isEqualTo("EMPLOYER");
        assertThat(row.get("recipient_text")).isEqualTo("  Acme S.A. - RRHH ñ  ");
    }

    @Test
    void recipientWithLineBreakIsRejected() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"uno\\ndos\"}").andExpect(status().isBadRequest());
        assertThat(certificateCount()).isZero();
    }

    @Test
    void recipientLongerThanEightyCharactersIsRejected() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"" + "a".repeat(81) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void recipientTypeWithoutTextIsRejected() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\"}").andExpect(status().isBadRequest());
        generate(studentToken, "{\"recipientText\":\"Obra social\"}").andExpect(status().isBadRequest());
    }

    @Test
    void recipientWithBracesIsRejectedSoItCannotInjectTemplateMarkers() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"{holder}\"}").andExpect(status().isBadRequest());
    }

    @Test
    void rejectedRecipientDoesNotConsumeTheDailyLimit() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\"}").andExpect(status().isBadRequest());

        generate(studentToken, "{}").andExpect(status().isCreated());
    }

    @Test
    void windowNotYetOpenIsRejected() throws Exception {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 4, 10, 0));

        generate(studentToken, "{\"registrationId\":" + registrationId + "}").andExpect(status().isUnprocessableEntity());
        assertThat(certificateCount()).isZero();
    }

    @Test
    void overdueDebtInFullModeBlocksGeneration() throws Exception {
        setBillingMode("FULL");
        addOverdueInstallment();

        generate(studentToken, "{}").andExpect(status().isUnprocessableEntity());
        assertThat(certificateCount()).isZero();
    }

    @Test
    void studentWithoutDebtGeneratesInFullMode() throws Exception {
        setBillingMode("FULL");

        generate(studentToken, "{}").andExpect(status().isCreated());
    }

    @Test
    void mirrorModeStudentCannotSelfGenerate() throws Exception {
        setBillingMode("MIRROR");

        generate(studentToken, "{}").andExpect(status().isUnprocessableEntity());
        assertThat(certificateCount()).isZero();
    }

    @Test
    void anotherStudentsRegistrationIsNotFound() throws Exception {
        generate(strangerToken, "{\"registrationId\":" + registrationId + "}").andExpect(status().isNotFound());
    }

    @Test
    void studentWithoutRegistrationIsNotEligible() throws Exception {
        generate(strangerToken, "{}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unauthenticatedGenerationIsRejected() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void downloadReturnsThePdfWithTheRecordedHashAndAuditsIt() throws Exception {
        Long id = issuedId(studentToken);

        MvcResult result = download(studentToken, id).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(sha256(pdf)).isEqualTo(jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class));
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_PDF_DOWNLOADED' and entity_id in (select id from exam_registration_certificate)", Long.class)).isEqualTo(1L);
    }

    @Test
    void repeatedDownloadsDoNotCountTowardsTheLimitNorCreateDocuments() throws Exception {
        Long id = issuedId(studentToken);

        download(studentToken, id).andExpect(status().isOk());
        download(studentToken, id).andExpect(status().isOk());

        assertThat(certificateCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_PDF_DOWNLOADED' and entity_id in (select id from exam_registration_certificate)", Long.class)).isEqualTo(2L);
    }

    @Test
    void missingStoredFileIsRegeneratedWithTheSameHash() throws Exception {
        Long id = issuedId(studentToken);
        String sha = jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class);
        String path = jdbc.queryForObject("select pdf_path from exam_registration_certificate", String.class);
        Files.delete(Path.of(certificatesDirectory).toAbsolutePath().normalize().resolve(path));

        MvcResult result = download(studentToken, id).andExpect(status().isOk()).andReturn();

        assertThat(sha256(result.getResponse().getContentAsByteArray())).isEqualTo(sha);
    }

    @Test
    void regenerationWithRecipientProducesTheRecordedHash() throws Exception {
        MvcResult created = generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"OSDE 210\"}")
                .andExpect(status().isCreated()).andReturn();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String sha = jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class);
        String path = jdbc.queryForObject("select pdf_path from exam_registration_certificate", String.class);
        Files.delete(Path.of(certificatesDirectory).toAbsolutePath().normalize().resolve(path));

        MvcResult result = download(studentToken, id).andExpect(status().isOk()).andReturn();

        assertThat(sha256(result.getResponse().getContentAsByteArray())).isEqualTo(sha);
    }

    @Test
    void tamperedStoredFileWithAnIntegrityMismatchIsNeverServed() throws Exception {
        Long id = issuedId(studentToken);
        jdbc.update("update exam_registration_certificate set snapshot = jsonb_set(snapshot, '{titleName}', '\"Otra materia\"')");
        String path = jdbc.queryForObject("select pdf_path from exam_registration_certificate", String.class);
        Files.write(Path.of(certificatesDirectory).toAbsolutePath().normalize().resolve(path), new byte[]{1, 2, 3});

        download(studentToken, id).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_PDF_HASH_MISMATCH' and entity_id in (select id from exam_registration_certificate)", Long.class)).isEqualTo(1L);
    }

    @Test
    void anotherStudentCannotDownloadTheDocument() throws Exception {
        Long id = issuedId(studentToken);

        download(strangerToken, id).andExpect(status().isNotFound());
    }

    @Test
    void downloadAfterTheExamDayIsNotFound() throws Exception {
        Long id = issuedId(studentToken);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 21, 8, 0));

        download(studentToken, id).andExpect(status().isNotFound());
    }

    @Test
    void publicVerificationShowsTheDocumentWithMaskedIdentity() throws Exception {
        issuedId(studentToken);
        String code = jdbc.queryForObject("select code from exam_registration_certificate", String.class);

        mockMvc.perform(get("/public/certificates/{code}", code).with(clientAddress("203.0.113." + ThreadLocalRandom.current().nextInt(1, 250))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentKind").value("EXAM_REGISTRATION"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.graduationDate").value("2026-11-20"))
                .andExpect(jsonPath("$.maskedDocument").value(org.hamcrest.Matchers.startsWith("*")));
    }

    @Test
    void publicVerificationShowsExpiredAfterTheExamDay() throws Exception {
        issuedId(studentToken);
        String code = jdbc.queryForObject("select code from exam_registration_certificate", String.class);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 21, 8, 0));

        mockMvc.perform(get("/public/certificates/{code}", code).with(clientAddress("198.51.100." + ThreadLocalRandom.current().nextInt(1, 250))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void publicVerificationShowsRevoked() throws Exception {
        issuedId(studentToken);
        String code = jdbc.queryForObject("select code from exam_registration_certificate", String.class);
        jdbc.update("update exam_registration_certificate set status = 'REVOKED', revoked_at = ?, revoked_by_id = issued_by_id, revocation_reason = 'x'",
                java.sql.Timestamp.valueOf("2026-11-10 11:00:00"));

        mockMvc.perform(get("/public/certificates/{code}", code).with(clientAddress("192.0.2." + ThreadLocalRandom.current().nextInt(1, 250))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
    }

}
