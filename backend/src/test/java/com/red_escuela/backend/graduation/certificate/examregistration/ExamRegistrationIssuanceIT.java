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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Value;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
        deleteCertificates();
        setBillingMode("OFF");
    }

    @AfterEach
    void restoreBillingMode() {
        deleteCertificates();
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

    @MockitoSpyBean
    private CertificatePdfRenderer renderer;
    @MockitoSpyBean
    private CertificateDigest digest;
    @MockitoSpyBean
    private VerifiableDocumentRegistry verifiableDocumentRegistry;
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;
    @Value("${app.certificates.directory}")
    private String certificatesDirectory;

    private ResultActions generate(String token, String json) throws Exception {
        var request = post(PATH).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        return mockMvc.perform(json == null ? request : request.content(json));
    }

    private ResultActions download(String token, Long id) throws Exception {
        return mockMvc.perform(get("/me/procedures/approved/EXAM_REGISTRATION_CERTIFICATE/{id}/pdf", id)
                .header("Authorization", "Bearer " + token));
    }

    private void failRegistration() {
        transactions.executeWithoutResult(status ->
                doThrow(new IllegalStateException("registry down")).when(verifiableDocumentRegistry).register(any(), any(), any()));
    }

    private void deleteCertificates() {
        jdbc.update("delete from verifiable_document where document_type = 'EXAM_REGISTRATION_CERTIFICATE'");
        jdbc.update("delete from exam_registration_certificate");
    }

    private long certificateCount() {
        return jdbc.queryForObject("select count(*) from exam_registration_certificate", Long.class);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private Long issuedId(String token) throws Exception {
        MvcResult result = generate(token, "{}").andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void generationPersistsSnapshotHashEngineVersionAndTemplate() throws Exception {
        generate(studentToken, "{\"registrationId\":" + registrationId + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("EXAM_REGISTRATION_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T00:00:00"))
                .andExpect(jsonPath("$.verificationUrl").value(Matchers.containsString("/verificar/")));

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
    void recipientIsStoredTrimmedAndOtherwiseLiterally() throws Exception {
        generate(studentToken, "{\"recipientType\":\"EMPLOYER\",\"recipientText\":\"  Acme S.A. - RRHH ñ  \"}")
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap("select recipient_type, recipient_text from exam_registration_certificate");
        assertThat(row.get("recipient_type")).isEqualTo("EMPLOYER");
        assertThat(row.get("recipient_text")).isEqualTo("Acme S.A. - RRHH ñ");
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
        Long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
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
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_PDF_HASH_MISMATCH' and entity_id in (select id from exam_registration_certificate)", Long.class)).isEqualTo(2L);
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

    private void deleteStoredFile() throws Exception {
        String path = jdbc.queryForObject("select pdf_path from exam_registration_certificate", String.class);
        Files.delete(Path.of(certificatesDirectory).toAbsolutePath().normalize().resolve(path));
    }

    @Test
    void recipientOnlyWithSpacesIsRejected() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXAM_REGISTRATION_RECIPIENT"));
        assertThat(certificateCount()).isZero();
    }

    @Test
    void invalidRecipientReportsItsDomainErrorCode() throws Exception {
        generate(studentToken, "{\"recipientType\":\"OTHER\",\"recipientText\":\"{holder}\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXAM_REGISTRATION_RECIPIENT"));
    }

    @Test
    void alteredStoredFileIsAuditedAndRegeneratedWithTheRecordedHash() throws Exception {
        Long id = issuedId(studentToken);
        String sha = jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class);
        String path = jdbc.queryForObject("select pdf_path from exam_registration_certificate", String.class);
        Files.write(Path.of(certificatesDirectory).toAbsolutePath().normalize().resolve(path), new byte[]{9, 9, 9});

        MvcResult result = download(studentToken, id).andExpect(status().isOk()).andReturn();

        assertThat(sha256(result.getResponse().getContentAsByteArray())).isEqualTo(sha);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'CERTIFICATE_DOCUMENT' and action = 'DOCUMENT_PDF_HASH_MISMATCH' and entity_id in (select id from exam_registration_certificate)", Long.class)).isEqualTo(1L);
    }

    @Test
    void issuedSnapshotDoesNotChangeWhenTheSourceDataChangesAfterwards() throws Exception {
        Long id = issuedId(studentToken);
        String sha = jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class);
        jdbc.update("update person set name = 'Otro', last_name = 'Cambiado' where id = (select person_id from student where id = (select student_id from exam_registration_certificate))");
        jdbc.update("update subject set name = 'Materia renombrada' where id = ?", subjectId);
        jdbc.update("update exam_board set exam_date = ? where id = ?", Timestamp.valueOf("2026-12-15 09:00:00"), boardId);
        jdbc.update("update institution set name = 'Otra institucion' where id = (select institution_id from student where id = (select student_id from exam_registration_certificate))");
        deleteStoredFile();

        MvcResult result = download(studentToken, id).andExpect(status().isOk()).andReturn();

        assertThat(sha256(result.getResponse().getContentAsByteArray())).isEqualTo(sha);
        assertThat(jdbc.queryForObject("select pdf_sha256 from exam_registration_certificate", String.class)).isEqualTo(sha);
    }

    @Test
    void verificationUrlIsBuiltFromTheConfiguredFrontendBaseUrl() throws Exception {
        issuedId(studentToken);
        String code = jdbc.queryForObject("select code from exam_registration_certificate", String.class);
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;

        generate(studentToken, "{}").andExpect(jsonPath("$.verificationUrl").value(base + "/verificar/" + code));
    }

    @Test
    void approvedListShowsValidExpiredAndRevokedDocumentsWithTheirVerifiableFlag() throws Exception {
        issuedId(studentToken);
        jdbc.update("insert into exam_registration_certificate (code, student_id, registration_id, exam_board_id, status, issued_at, expires_at,"
                + " issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, render_engine_version, revoked_at, revoked_by_id)"
                + " select upper(substr(md5(random()::text), 1, 26)), student_id, registration_id, exam_board_id, 'REVOKED', issued_at, expires_at,"
                + " issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, render_engine_version, issued_at, issued_by_id"
                + " from exam_registration_certificate where status = 'VALID'");
        jdbc.update("insert into exam_registration_certificate (code, student_id, registration_id, exam_board_id, status, issued_at, expires_at,"
                + " issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, render_engine_version)"
                + " select upper(substr(md5(random()::text), 1, 26)), student_id, registration_id, exam_board_id, 'EXPIRED', issued_at, expires_at,"
                + " issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, render_engine_version"
                + " from exam_registration_certificate where status = 'VALID'");

        mockMvc.perform(get("/me/procedures/approved").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.status == 'VALID')].verifiable").value(true))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.status == 'REVOKED')].verifiable").value(false))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.status == 'EXPIRED')].verifiable").value(false));
    }

    @Test
    void validDocumentIsListedAsExpiredOnceTheExamDayHasPassed() throws Exception {
        issuedId(studentToken);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 21, 8, 0));

        mockMvc.perform(get("/me/procedures/approved").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE')].status").value("EXPIRED"))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE')].verifiable").value(false));
    }

    private Long generateWithRecipient(String type, String text) throws Exception {
        MvcResult result = generate(studentToken, "{\"recipientType\":\"" + type + "\",\"recipientText\":\"" + text + "\"}").andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private List<String> statusesOfCertificates() {
        return jdbc.queryForList("select status from exam_registration_certificate order by id", String.class);
    }

    @Test
    void sameRecipientOnTheSameDayReusesTheCurrentDocumentWithoutCounting() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");

        Long second = generateWithRecipient("EMPLOYER", "Empresa A");

        assertThat(second).isEqualTo(first);
        assertThat(statusesOfCertificates()).containsExactly("VALID");
    }

    @Test
    void sameRecipientOnALaterDayStillReusesTheCurrentDocument() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        Long second = generateWithRecipient("EMPLOYER", "Empresa A");

        assertThat(second).isEqualTo(first);
        assertThat(statusesOfCertificates()).containsExactly("VALID");
    }

    @Test
    void differentRecipientOnALaterDayIssuesANewDocumentAndReplacesTheCurrentOne() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        Long second = generateWithRecipient("HEALTH_INSURANCE", "Obra social B");

        assertThat(second).isNotEqualTo(first);
        assertThat(statusesOfCertificates()).containsExactly("REPLACED", "VALID");
        Map<String, Object> current = jdbc.queryForMap("select recipient_type, recipient_text from exam_registration_certificate where status = 'VALID'");
        assertThat(current.get("recipient_type")).isEqualTo("HEALTH_INSURANCE");
        assertThat(current.get("recipient_text")).isEqualTo("Obra social B");
    }

    @Test
    void changingFromNoRecipientToARecipientIsADifferentRecipient() throws Exception {
        Long first = issuedId(studentToken);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        Long second = generateWithRecipient("OTHER", "Club");

        assertThat(second).isNotEqualTo(first);
        assertThat(statusesOfCertificates()).containsExactly("REPLACED", "VALID");
    }

    @Test
    void differentRecipientOnTheSameDayHitsTheLimitAndKeepsTheCurrentDocument() throws Exception {
        generateWithRecipient("EMPLOYER", "Empresa A");

        generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"Obra social B\"}")
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ISSUANCE_LIMIT_REACHED"));

        assertThat(statusesOfCertificates()).containsExactly("VALID");
        assertThat(jdbc.queryForObject("select recipient_text from exam_registration_certificate", String.class)).isEqualTo("Empresa A");
    }

    @Test
    void replacedDocumentCannotBeDownloadedAndIsListedAsReplaced() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        Long second = generateWithRecipient("HEALTH_INSURANCE", "Obra social B");

        download(studentToken, first).andExpect(status().isNotFound());
        download(studentToken, second).andExpect(status().isOk());
        mockMvc.perform(get("/me/procedures/approved").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.id == " + first + ")].status").value("REPLACED"))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.id == " + first + ")].verifiable").value(false))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.id == " + second + ")].status").value("VALID"))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_REGISTRATION_CERTIFICATE' && @.id == " + second + ")].verifiable").value(true));
    }

    @Test
    void replacingCountsTowardsTheDailyLimitForOtherRegistrations() throws Exception {
        generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        generateWithRecipient("HEALTH_INSURANCE", "Obra social B");
        Long other = secondRegistrationOn(LocalDateTime.of(2026, 11, 25, 9, 0));

        generate(studentToken, "{\"registrationId\":" + other + "}").andExpect(status().isTooManyRequests());
    }

    private static final int CONCURRENCY_ROUNDS = 8;

    @Test
    void concurrentRequestsForTwoRegistrationsLetOnlyOnePassTheDailyLimit() throws Exception {
        Long other = secondRegistrationOn(LocalDateTime.of(2026, 11, 25, 9, 0));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
                deleteCertificates();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Integer>> results = List.of(registrationId, other).stream().map(target -> executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return generate(studentToken, "{\"registrationId\":" + target + "}").andReturn().getResponse().getStatus();
                })).toList();
                ready.await();
                go.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }
                assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(201, 429);
                assertThat(certificateCount()).as("round " + round).isEqualTo(1L);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> storedFileNames() throws Exception {
        Path directory = Path.of(certificatesDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void sameRecipientWithSurroundingSpacesIsTheSameRecipient() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");

        Long second = generateWithRecipient("EMPLOYER", "  Empresa A  ");

        assertThat(second).isEqualTo(first);
        assertThat(statusesOfCertificates()).containsExactly("VALID");
    }

    @Test
    void renderFailureWhileReplacingKeepsTheCurrentDocumentValid() throws Exception {
        generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        List<String> filesBefore = storedFileNames();
        doThrow(new IllegalStateException("render caído")).when(renderer).render(any(), any(), any(), any());

        generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"Obra social B\"}")
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        assertThat(statusesOfCertificates()).containsExactly("VALID");
        assertThat(storedFileNames()).isEqualTo(filesBefore);
    }

    @Test
    void failureAfterTheFileIsStoredRollsBackTheReplacementAndRemovesTheOrphanFile() throws Exception {
        generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        List<String> filesBefore = storedFileNames();
        doThrow(new IllegalStateException("digest caído")).when(digest).sha256(any());

        generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"Obra social B\"}")
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        assertThat(statusesOfCertificates()).containsExactly("VALID");
        assertThat(storedFileNames()).isEqualTo(filesBefore);
    }

    @Test
    void concurrentRequestsWithDifferentRecipientsOnTheSameRegistrationLeaveOneValidDocument() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
                deleteCertificates();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Integer>> results = List.of("Empresa A", "Empresa B").stream().map(text -> executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return generate(studentToken, "{\"recipientType\":\"EMPLOYER\",\"recipientText\":\"" + text + "\"}")
                            .andReturn().getResponse().getStatus();
                })).toList();
                ready.await();
                go.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }
                assertThat(statuses).as("round " + round).containsExactlyInAnyOrder(201, 429);
                assertThat(jdbc.queryForObject("select count(*) from exam_registration_certificate where status = 'VALID'", Long.class))
                        .as("round " + round).isEqualTo(1L);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static final AtomicInteger PUBLIC_CLIENTS = new AtomicInteger(1);

    private ResultActions verifyPublicly(String code) throws Exception {
        String address = "203.0.113." + PUBLIC_CLIENTS.incrementAndGet() % 250;
        RequestPostProcessor client = request -> {
            request.setRemoteAddr(address);
            return request;
        };
        return mockMvc.perform(get("/public/documents/{code}", code).with(client));
    }

    private String codeOf(Long id) {
        return jdbc.queryForObject("select code from exam_registration_certificate where id = ?", String.class, id);
    }

    @Test
    void issuedDocumentIsRegisteredAndVerifiableWithMinimalData() throws Exception {
        Long id = generateWithRecipient("EMPLOYER", "Empresa Secreta SA");
        String code = codeOf(id);

        assertThat(jdbc.queryForObject("select count(*) from verifiable_document where code = ? and document_type = 'EXAM_REGISTRATION_CERTIFICATE' and source_id = ?",
                Long.class, code, id)).isEqualTo(1L);
        MvcResult result = verifyPublicly(code).andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("EXAM_REGISTRATION_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.maskedDocument").value(Matchers.startsWith("*")))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-21T00:00:00"))
                .andExpect(jsonPath("$.replacedAt").doesNotExist())
                .andExpect(jsonPath("$.revokedAt").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Empresa Secreta", "EMPLOYER", "Materia", "recipient");
    }

    @Test
    void publicVerificationReportsExpiredAfterTheExamDay() throws Exception {
        String code = codeOf(issuedId(studentToken));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 21, 8, 0));

        verifyPublicly(code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void publicVerificationReportsRevokedWithTheRevocationDate() throws Exception {
        String code = codeOf(issuedId(studentToken));
        jdbc.update("update exam_registration_certificate set status = 'REVOKED', revoked_at = ?, revoked_by_id = issued_by_id, revocation_reason = 'motivo interno'",
                Timestamp.valueOf("2026-11-10 11:00:00"));

        MvcResult result = verifyPublicly(code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").value("2026-11-10T11:00:00")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("motivo interno");
    }

    @Test
    void publicVerificationOfAReplacedDocumentReportsReplacedWithTheReplacementDate() throws Exception {
        Long first = generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        Long second = generateWithRecipient("HEALTH_INSURANCE", "Obra social B");

        verifyPublicly(codeOf(first)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REPLACED"))
                .andExpect(jsonPath("$.replacedAt").value("2026-11-11T10:00:00"));
        verifyPublicly(codeOf(second)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.replacedAt").doesNotExist());
    }

    @Test
    void unregisteredCodeIsNotFound() throws Exception {
        verifyPublicly("0".repeat(26)).andExpect(status().isNotFound());
    }

    @Test
    void failedRegistrationRollsBackTheIssuanceAndTheFile() throws Exception {
        List<String> filesBefore = storedFileNames();
        failRegistration();

        generate(studentToken, "{}").andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(409));

        assertThat(certificateCount()).isZero();
        assertThat(storedFileNames()).isEqualTo(filesBefore);
    }

    @Test
    void failedRegistrationWhileReplacingRevertsThePreviousDocumentToValid() throws Exception {
        generateWithRecipient("EMPLOYER", "Empresa A");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        List<String> filesBefore = storedFileNames();
        failRegistration();

        generate(studentToken, "{\"recipientType\":\"HEALTH_INSURANCE\",\"recipientText\":\"Obra social B\"}")
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(409));

        assertThat(statusesOfCertificates()).containsExactly("VALID");
        assertThat(storedFileNames()).isEqualTo(filesBefore);
    }

    @Test
    void publicVerificationIsValidOneSecondBeforeTheExpiryInstant() throws Exception {
        String code = codeOf(issuedId(studentToken));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 20, 23, 59, 59));

        verifyPublicly(code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void publicVerificationIsExpiredExactlyAtTheExpiryInstant() throws Exception {
        String code = codeOf(issuedId(studentToken));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 21, 0, 0, 0));

        verifyPublicly(code).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
