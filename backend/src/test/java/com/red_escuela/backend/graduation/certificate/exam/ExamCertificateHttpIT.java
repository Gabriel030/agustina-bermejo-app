package com.red_escuela.backend.graduation.certificate.exam;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
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
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.template.PublishedTemplateFixtures;
import java.util.Map;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ExamCertificateHttpIT extends AbstractIntegrationTest {

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
    private CertificateFileStore fileStore;
    @Autowired
    private CertificateDigest digest;
    @Autowired
    private CertificatePdfRenderer renderer;
    @Autowired
    private ExamCertificateTemplate examTemplate;
    @Autowired
    private CertificateVerificationUrl verificationUrl;
    @Autowired
    private ExamCertificateRepository certificateRepository;
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private Long boardId;
    private Long openBoardId;
    private Long presentRegistrationId;
    private Long secondPresentRegistrationId;
    private Long debtorRegistrationId;
    private Long absentRegistrationId;
    private Long withdrawnRegistrationId;
    private Long openBoardRegistrationId;
    private String teacherToken;
    private String strangerTeacherToken;
    private String administrativeToken;
    private String directorToken;

    @BeforeEach
    void seedClosedBoardWithAttendees() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        setBillingMode("FULL");
        transactions.executeWithoutResult(status -> {
            Subject subject = subject();
            Term term = term();
            Person teacher = person("DOCENTE");
            Instructor instructor = instructor(teacher);
            Person stranger = person("DOCENTE");
            instructor(stranger);
            ExamBoard board = board(subject, term, ExamBoardStatus.CLOSED, instructor);
            ExamBoard open = board(subject, term, ExamBoardStatus.SCHEDULED, instructor);
            presentRegistrationId = registration(board, subject, term, ExamBoardRegistrationStatus.GRADED, false);
            secondPresentRegistrationId = registration(board, subject, term, ExamBoardRegistrationStatus.GRADED, false);
            debtorRegistrationId = registration(board, subject, term, ExamBoardRegistrationStatus.GRADED, true);
            absentRegistrationId = registration(board, subject, term, ExamBoardRegistrationStatus.ABSENT, false);
            withdrawnRegistrationId = registration(board, subject, term, ExamBoardRegistrationStatus.WITHDRAWN, false);
            openBoardRegistrationId = registration(open, subject, term, ExamBoardRegistrationStatus.REGISTERED, false);
            boardId = board.getId();
            openBoardId = open.getId();
            teacherToken = jwtService.generateAccessToken(teacher);
            strangerTeacherToken = jwtService.generateAccessToken(stranger);
            administrativeToken = jwtService.generateAccessToken(person("ADMINISTRATIVO"));
            directorToken = jwtService.generateAccessToken(person("DIRECTOR"));
        });
    }

    @AfterEach
    void restoreBillingMode() {
        setBillingMode("FULL");
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    private Subject subject() {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia IT " + UUID.randomUUID());
        entityManager.persist(subject);
        return subject;
    }

    private Term term() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private Person person(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        String suffix = UUID.randomUUID().toString();
        Person person = new Person();
        person.setName("Persona");
        person.setLastname("Apellido " + suffix.substring(0, 8));
        person.setDni(suffix.substring(0, 12));
        person.setUsername("examcert." + suffix);
        person.setEmail("examcert." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }

    private Instructor instructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private ExamBoard board(Subject subject, Term term, ExamBoardStatus status, Instructor tribunalMember) {
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(LocalDateTime.of(2026, 11, 5, 9, 0));
        board.setStatus(status);
        entityManager.persist(board);
        ExamBoardInstructor member = new ExamBoardInstructor();
        member.setExamBoard(board);
        member.setInstructor(tribunalMember);
        entityManager.persist(member);
        return board;
    }

    private Long registration(ExamBoard board, Subject subject, Term term, ExamBoardRegistrationStatus status, boolean withOverdueInstallment) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person("ALUMNO"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = new Program();
        program.setName("Carrera " + UUID.randomUUID());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
        entityManager.persist(programEnrollment);
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        enrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(status);
        registration.setRegisteredAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        entityManager.persist(registration);
        entityManager.flush();
        if (withOverdueInstallment) {
            BillingTestFixtures.invoice().forProgramEnrollment(programEnrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
        }
        return registration.getId();
    }

    private MvcResult batch(Long board, String token, Long... registrationIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (Long id : registrationIds) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        return mockMvc.perform(post("/exam-boards/{id}/exam-certificates/batch", board)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationIds\":[" + ids + "],\"recipientType\":\"OTHER\",\"recipientText\":\"Ministerio\"}")).andReturn();
    }

    private Long issuedCertificateId(Long registrationId) {
        return jdbc.queryForObject("select id from exam_certificate where registration_id = ? order by id desc limit 1", Long.class, registrationId);
    }

    private Long issueOne(Long registrationId) throws Exception {
        MvcResult result = batch(boardId, teacherToken, registrationId);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return issuedCertificateId(registrationId);
    }

    private int auditCount(String action, Long certificateId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, certificateId);
    }

    private byte[] download(Long certificateId, String token) throws Exception {
        return mockMvc.perform(get("/exam-certificates/{id}/pdf", certificateId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String decodeQr(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
        }
    }

    @Test
    void teacherBatchIssuesAndPdfIsDeterministicWithQrToTheConfiguredFrontend() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);
        String code = jdbc.queryForObject("select code from exam_certificate where id = ?", String.class, certificateId);
        String path = jdbc.queryForObject("select pdf_path from exam_certificate where id = ?", String.class, certificateId);
        String storedSha = jdbc.queryForObject("select pdf_sha256 from exam_certificate where id = ?", String.class, certificateId);

        byte[] first = download(certificateId, teacherToken);
        fileStore.deleteIfRolledBack(path);
        byte[] regenerated = download(certificateId, teacherToken);

        assertThat(regenerated).isEqualTo(first);
        assertThat(digest.sha256(first)).isEqualTo(storedSha);
        assertThat(decodeQr(first)).isEqualTo(frontendBaseUrl.replaceAll("/$", "") + "/verificar/" + code);
    }

    @Test
    void issuanceWithPublishedVersionStoresItsIdAndTemplateReference() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);

        Map<String, Object> row = jdbc.queryForMap("select template_version_id, template_ref from exam_certificate where id = ?", certificateId);
        assertThat(((Number) row.get("template_version_id")).longValue())
                .isEqualTo(PublishedTemplateFixtures.currentPublishedId(jdbc, "EXAM_CERTIFICATE"));
        assertThat(row.get("template_ref")).isEqualTo(PublishedTemplateFixtures.currentPublishedRef(jdbc, "EXAM_CERTIFICATE"));
    }

    @Test
    void republishingTheTemplateDoesNotChangeTheRegeneratedPdfNorItsHash() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);
        String issuedSha = jdbc.queryForObject("select pdf_sha256 from exam_certificate where id = ?", String.class, certificateId);
        PublishedTemplateFixtures.publishRebrandedVersion(jdbc, "EXAM_CERTIFICATE");
        jdbc.update("update exam_certificate set pdf_path = 'missing/file.pdf' where id = ?", certificateId);

        byte[] regenerated = download(certificateId, teacherToken);

        assertThat(digest.sha256(regenerated)).isEqualTo(issuedSha);
        assertThat(jdbc.queryForObject("select pdf_sha256 from exam_certificate where id = ?", String.class, certificateId)).isEqualTo(issuedSha);
        try (PDDocument document = Loader.loadPDF(regenerated)) {
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("INSTITUTO REPUBLICADO");
        }
    }

    @Test
    void certificateWithoutTemplateVersionRegeneratesTheBuiltInDesignWithTheSameHash() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);
        ExamCertificate stored = certificateRepository.findById(certificateId).orElseThrow();
        CertificateSnapshot builtIn = PublishedTemplateFixtures.withBuiltInRef(stored.getSnapshot(), ExamCertificateTemplate.EXAM_BUILT_IN_V1);
        CertificateCode code = stored.certificateCode();
        byte[] legacy = renderer.render(examTemplate.definitionFor(builtIn, stored.getExpiresAt().toLocalDate(), stored.getRecipientType(),
                stored.getRecipientText()), builtIn, code, verificationUrl.of(code));
        jdbc.update("update exam_certificate set template_version_id = null, template_ref = ?, pdf_sha256 = ?, pdf_path = 'missing/file.pdf', "
                + "snapshot = jsonb_set(snapshot - 'extraMarkers', '{templateRef}', to_jsonb(?::text)) where id = ?",
                ExamCertificateTemplate.EXAM_BUILT_IN_V1, digest.sha256(legacy), ExamCertificateTemplate.EXAM_BUILT_IN_V1, certificateId);

        byte[] regenerated = download(certificateId, teacherToken);

        assertThat(regenerated).isEqualTo(legacy);
        try (PDDocument document = Loader.loadPDF(regenerated)) {
            assertThat(new PDFTextStripper().getText(document)).contains("CONSTANCIA DE EXAMEN RENDIDO");
        }
    }

    @Test
    void everyDownloadIsAuditedAndAnotherTeachersBoardIsNotFound() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);

        download(certificateId, teacherToken);
        download(certificateId, administrativeToken);
        mockMvc.perform(get("/exam-certificates/{id}/pdf", certificateId).header("Authorization", "Bearer " + strangerTeacherToken))
                .andExpect(status().isNotFound());

        assertThat(auditCount("DOCUMENT_PDF_DOWNLOADED", certificateId)).isEqualTo(2);
    }

    @Test
    void anotherTeacherCannotListNorIssueOnAForeignBoard() throws Exception {
        mockMvc.perform(get("/exam-boards/{id}/exam-certificates/candidates", boardId)
                .header("Authorization", "Bearer " + strangerTeacherToken)).andExpect(status().isNotFound());

        assertThat(batch(boardId, strangerTeacherToken, presentRegistrationId).getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where exam_board_id = ?", Integer.class, boardId)).isZero();
    }

    @Test
    void batchSkipsTheDebtorWithoutAmountsAndIssuesTheRest() throws Exception {
        MvcResult result = batch(boardId, teacherToken, presentRegistrationId, debtorRegistrationId, absentRegistrationId,
                withdrawnRegistrationId);

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        mockMvc.perform(get("/exam-boards/{id}/exam-certificates/candidates", boardId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.registrationId == " + debtorRegistrationId + ")].availability").value("NOT_ELIGIBLE_DEBT"))
                .andExpect(jsonPath("$[?(@.registrationId == " + presentRegistrationId + ")].availability").value("ALREADY_ISSUED"));
        assertThat(body).contains("\"issuedCount\":1", "\"skippedCount\":3");
        assertThat(body).contains("\"outcome\":\"ISSUED\"", "\"outcome\":\"DEBT\"", "\"outcome\":\"ABSENT\"", "\"outcome\":\"NOT_REGISTERED\"");
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where registration_id = ?", Integer.class, debtorRegistrationId)).isZero();
        assertThat(body).doesNotContainIgnoringCase(debtorLastname()).doesNotContainIgnoringCase("amount").doesNotContainIgnoringCase("monto").doesNotContain("15000");
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where registration_id = ?", Integer.class, debtorRegistrationId))
                .isZero();
    }

    private String debtorLastname() {
        return jdbc.queryForObject("select p.last_name from exam_board_registration r join subject_section_enrollment e on e.id = r.program_section_enrollment_id "
                + "join student s on s.id = e.student_id join person p on p.id = s.person_id where r.id = ?", String.class, debtorRegistrationId);
    }

    @Test
    void twoConcurrentBatchesOfTheSameBoardIssueOnceAndNeitherFails() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return batch(boardId, teacherToken, presentRegistrationId, secondPresentRegistrationId).getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(results.get(0).get()).isEqualTo(200);
            assertThat(results.get(1).get()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where exam_board_id = ?", Integer.class, boardId)).isEqualTo(2);
    }

    @Test
    void anAlreadyIssuedRegistrationIsReportedAndNotDuplicated() throws Exception {
        issueOne(presentRegistrationId);

        MvcResult second = batch(boardId, teacherToken, presentRegistrationId, secondPresentRegistrationId);

        assertThat(second.getResponse().getContentAsString()).contains("\"outcome\":\"ALREADY_ISSUED\"", "\"outcome\":\"ISSUED\"");
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where registration_id = ?", Integer.class, presentRegistrationId))
                .isEqualTo(1);
    }

    @Test
    void onlyTheDirectorRevokesAndARevokedCertificateIsReissuedInTheBatch() throws Exception {
        Long certificateId = issueOne(presentRegistrationId);
        String revokeBody = "{\"reason\":\"Datos incorrectos\"}";

        for (String token : new String[]{teacherToken, administrativeToken}) {
            mockMvc.perform(post("/exam-certificates/{id}/revoke", certificateId).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(revokeBody)).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select status from exam_certificate where id = ?", String.class, certificateId)).isEqualTo("VALID");

        mockMvc.perform(post("/exam-certificates/{id}/revoke", certificateId).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(revokeBody)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from exam_certificate where id = ?", String.class, certificateId)).isEqualTo("REVOKED");
        assertThat(auditCount("EXAM_CERTIFICATE_REVOKED", certificateId)).isEqualTo(1);

        MvcResult reissue = batch(boardId, teacherToken, presentRegistrationId);

        assertThat(reissue.getResponse().getContentAsString()).contains("\"outcome\":\"ISSUED\"");
        assertThat(issuedCertificateId(presentRegistrationId)).isNotEqualTo(certificateId);
    }

    @Test
    void aBoardThatWasNotHeldRejectsListingAndIssuance() throws Exception {
        mockMvc.perform(get("/exam-boards/{id}/exam-certificates/candidates", openBoardId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EXAM_CERTIFICATE_BOARD_NOT_HELD"));

        MvcResult result = batch(openBoardId, teacherToken, openBoardRegistrationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentAsString()).contains("EXAM_CERTIFICATE_BOARD_NOT_HELD");
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where exam_board_id = ?", Integer.class, openBoardId)).isZero();
    }

    @Test
    void withBillingOffTheDebtorIsIssuedWithoutAnyPaymentCheck() throws Exception {
        setBillingMode("OFF");

        MvcResult result = batch(boardId, teacherToken, debtorRegistrationId);

        assertThat(result.getResponse().getContentAsString()).contains("\"outcome\":\"ISSUED\"").doesNotContain("DEBT");
        assertThat(jdbc.queryForObject("select count(*) from exam_certificate where registration_id = ?", Integer.class, debtorRegistrationId))
                .isEqualTo(1);
    }
}
