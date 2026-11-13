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
import com.red_escuela.backend.graduation.certificate.regular.dto.IssueRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;
import com.red_escuela.backend.graduation.certificate.regular.exception.ConcurrentRegularCertificateIssueException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NotRegularException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingEvidenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateExpirer;
import com.red_escuela.backend.graduation.certificate.regular.usecase.GetRegularCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.IssueRegularCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificatesUseCase;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ListRegularCertificateRequestsUseCase;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class IssueRegularCertificateIntegrationTest extends AbstractIntegrationTest {

    private static final String EVIDENCE = "Recibo 1234 verificado en el sistema contable";

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
    private IssueRegularCertificateUseCase issueUseCase;
    @Autowired
    private GetRegularCertificatePdfUseCase pdfUseCase;
    @Autowired
    private ListRegularCertificatesUseCase listUseCase;
    @Autowired
    private ListRegularCertificateRequestsUseCase listRequestsUseCase;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private RegularCertificateExpirer expirer;
    @Autowired
    private RegularStudentCertificateRepository certificateRepository;
    @Autowired
    private RegularCertificateRequestRepository requestRepository;

    private Long enrollmentId;
    private Long studentId;
    private Long sectionEnrollmentId;
    private int recordedDays;
    private Person staff;
    private String staffToken;
    private String studentToken;

    @BeforeEach
    void seedEnrolledStudentWithAttendance() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
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
            staff = persistPerson("ADMINISTRATIVO");
            staffToken = jwtService.generateAccessToken(staff);
            studentToken = jwtService.generateAccessToken(persistPerson("ALUMNO"));
        });
        recordedDays = 0;
        recordAttendance(8, 1);
        setBillingMode("OFF");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
    }

    @AfterEach
    void restoreBillingModeAndSecurity() {
        SecurityContextHolder.clearContext();
        setBillingMode("FULL");
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
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

    private RegularCertificateResponse issue(boolean confirmed, String evidence) {
        return issueUseCase.issue(new IssueRegularCertificateRequest(RegularityKind.PROGRAM, enrollmentId, confirmed, evidence));
    }

    private RegularCertificateResponse issueConfirmed() {
        return issue(true, EVIDENCE);
    }

    private List<RegularCertificateRequest> requests() {
        return requestRepository.findByStudentIdOrderByRequestedAtDesc(studentId);
    }

    private String issueBody(boolean confirmed) {
        return "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + ",\"standingConfirmed\":" + confirmed
                + ",\"standingEvidence\":\"" + EVIDENCE + "\"}";
    }

    @Test
    void issuesInOffModeWithManualConfirmationAndStoresPdf() {
        RegularCertificateResponse response = issueConfirmed();

        RegularStudentCertificate stored = certificateRepository.findById(response.id()).orElseThrow();
        RegularCertificatePdf pdf = pdfUseCase.pdfOf(response.id());
        assertThat(response.status()).isEqualTo(RegularCertificateStatus.VALID);
        assertThat(response.standingBasis()).isEqualTo(StandingBasis.MANUALLY_CONFIRMED);
        assertThat(response.billingModeAtIssue()).isEqualTo("OFF");
        assertThat(response.expiresAt()).isEqualTo(response.issuedAt().plusDays(30));
        assertThat(stored.getStandingEvidence()).isEqualTo(EVIDENCE);
        assertThat(stored.getStandingConfirmedById()).isEqualTo(staff.getId());
        assertThat(pdf.content()).hasSizeGreaterThan(100);
        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome).containsExactly(RegularRequestOutcome.ISSUED);
        assertThat(requests().get(0).getCertificateId()).isEqualTo(response.id());
    }

    @Test
    void studentWithAbsencesAboveLimitCannotGetCertificate() {
        recordAttendance(0, 8);

        assertThatThrownBy(this::issueConfirmed).isInstanceOf(NotRegularException.class);

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
        assertThat(requests()).hasSize(1);
        assertThat(requests().get(0).getOutcome()).isEqualTo(RegularRequestOutcome.NOT_REGULAR);
        assertThat(requests().get(0).getReasons()).containsExactly("FREE_IN_ACTIVE_SECTION");
    }

    @Test
    void studentWithAbsencesWithinLimitIsRegular() {
        assertThat(issueConfirmed().status()).isEqualTo(RegularCertificateStatus.VALID);
    }

    @Test
    void fullModeWithOverdueInvoiceIsBlockedAndTheAttemptStaysRecorded() {
        setBillingMode("FULL");
        addOverdueInstallment();

        assertThatThrownBy(this::issueConfirmed).isInstanceOf(StudentHasDebtException.class);

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
        assertThat(requests()).hasSize(1);
        assertThat(requests().get(0).getOutcome()).isEqualTo(RegularRequestOutcome.REJECTED);
        assertThat(requests().get(0).getReasons()).containsExactly("STUDENT_HAS_DEBT");
    }

    @Test
    void fullModeWithoutDebtIssuesWithBillingVerifiedBasisIgnoringTheCheckbox() {
        setBillingMode("FULL");

        RegularCertificateResponse response = issue(false, null);

        assertThat(response.standingBasis()).isEqualTo(StandingBasis.BILLING_VERIFIED);
        assertThat(response.billingModeAtIssue()).isEqualTo("FULL");
    }

    @Test
    void blockedAttemptsStayRecordedAsRequests() {
        assertThatThrownBy(() -> issue(false, null)).isInstanceOf(StandingNotConfirmedException.class);
        assertThatThrownBy(() -> issue(true, "x".repeat(501))).isInstanceOf(StandingEvidenceInvalidException.class);
        issueConfirmed();
        assertThatThrownBy(this::issueConfirmed).isInstanceOf(RegularCertificateAlreadyIssuedException.class);

        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome).containsExactlyInAnyOrder(
                RegularRequestOutcome.REJECTED, RegularRequestOutcome.REJECTED, RegularRequestOutcome.ISSUED,
                RegularRequestOutcome.ALREADY_ISSUED);
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
    }

    @Test
    void concurrentRequestsCreateOnlyOneValidCertificate() throws Exception {
        int threads = 6;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<RegularCertificateResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<RegularCertificateResponse> attempt = () -> {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
                barrier.await();
                try {
                    return issueConfirmed();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            futures.add(executor.submit(attempt));
        }
        int issued = 0;
        int rejected = 0;
        for (Future<RegularCertificateResponse> future : futures) {
            try {
                future.get();
                issued++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(ConcurrentRegularCertificateIssueException.class,
                        RegularCertificateAlreadyIssuedException.class);
                rejected++;
            }
        }
        executor.shutdownNow();

        assertThat(issued).isEqualTo(1);
        assertThat(rejected).isEqualTo(threads - 1);
        assertThat(certificateRepository.findByProgramEnrollmentIdAndStatus(enrollmentId, RegularCertificateStatus.VALID)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from regular_student_certificate where status = 'VALID' and student_id = ?", Long.class, studentId)).isEqualTo(1L);
    }

    @Test
    void certificateIsValidUntilThirtyDaysAfterIssueAndTheJobExpiresItAfterwards() {
        RegularCertificateResponse response = issueConfirmed();

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 10, 10, 0));
        assertThat(expirer.expireDue()).isZero();
        assertThat(listUseCase.list(studentId, RegularCertificateStatus.VALID)).extracting(RegularCertificateResponse::id)
                .containsExactly(response.id());

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 10, 10, 1));
        assertThat(listUseCase.list(studentId, RegularCertificateStatus.EXPIRED)).extracting(RegularCertificateResponse::id)
                .containsExactly(response.id());
        assertThat(expirer.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(certificateRepository.findById(response.id()).orElseThrow().getStatus()).isEqualTo(RegularCertificateStatus.EXPIRED);
        assertThat(expirer.expireDue()).isZero();
    }

    @Test
    void expiredCertificateIsReplacedByANewIssueWithoutWaitingForTheJob() {
        RegularCertificateResponse first = issueConfirmed();
        assertThatThrownBy(this::issueConfirmed).isInstanceOf(RegularCertificateAlreadyIssuedException.class);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 11, 10, 0));
        RegularCertificateResponse second = issueConfirmed();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.expiresAt()).isEqualTo(LocalDateTime.of(2027, 1, 10, 10, 0));
        assertThat(certificateRepository.findById(first.id()).orElseThrow().getStatus()).isEqualTo(RegularCertificateStatus.EXPIRED);
        assertThat(jdbc.queryForObject("select count(*) from regular_student_certificate where status = 'VALID' and student_id = ?", Long.class, studentId)).isEqualTo(1L);
    }

    @Test
    void staffIssuesListsAndDownloadsThroughTheEndpointsAndStudentsAreForbidden() throws Exception {
        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(true)))
                .andExpect(status().isForbidden());

        String json = mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.standingBasis").value("MANUALLY_CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/regular-certificates").param("studentId", studentId.toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/regular-certificates/" + id).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/regular-certificates/" + id + "/pdf").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/regular-certificate-requests").param("outcome", "ISSUED").param("studentId", studentId.toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].certificateId").value(id));
        mockMvc.perform(get("/regular-certificates/" + id).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGULAR_CERTIFICATE_ALREADY_ISSUED"));
        mockMvc.perform(get("/regular-certificates/999999").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpointRejectsMissingConfirmationInOffModeWithTheDocumentedCode() throws Exception {
        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STANDING_NOT_CONFIRMED"));
        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome).containsExactly(RegularRequestOutcome.REJECTED);
    }

    @Test
    void certificateAndRequestResponsesCarryStudentAndProgramNames() throws Exception {
        String expectedStudentName = transactions.execute(status -> {
            Person person = entityManager.find(Student.class, studentId).getPerson();
            return person.getName() + " " + person.getLastname();
        });
        String expectedProgramName = transactions.execute(status -> entityManager.find(ProgramEnrollment.class, enrollmentId).getProgram().getName());

        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(issueBody(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.studentName").value(expectedStudentName))
                .andExpect(jsonPath("$.programName").value(expectedProgramName));
        mockMvc.perform(get("/regular-certificates").param("studentId", studentId.toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(jsonPath("$[0].studentId").value(studentId))
                .andExpect(jsonPath("$[0].studentName").value(expectedStudentName))
                .andExpect(jsonPath("$[0].programName").value(expectedProgramName));
        mockMvc.perform(get("/regular-certificate-requests").param("studentId", studentId.toString())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentId))
                .andExpect(jsonPath("$[0].studentName").value(expectedStudentName))
                .andExpect(jsonPath("$[0].programName").value(expectedProgramName))
                .andExpect(jsonPath("$[0].rejectionReason").doesNotExist());
        mockMvc.perform(get("/regular-certificate-requests").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingRequestsResolvesNamesInBatchWithoutOneQueryPerRequest() {
        assertThatThrownBy(() -> issue(false, null)).isInstanceOf(StandingNotConfirmedException.class);
        assertThatThrownBy(() -> issue(false, null)).isInstanceOf(StandingNotConfirmedException.class);
        assertThatThrownBy(() -> issue(false, null)).isInstanceOf(StandingNotConfirmedException.class);
        issueConfirmed();

        List<RegularCertificateRequestResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, 5,
                () -> listRequestsUseCase.list(studentId, null));

        assertThat(responses).hasSize(4);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.studentName()).isNotBlank();
            assertThat(response.programName()).isNotBlank();
        });
    }
}
