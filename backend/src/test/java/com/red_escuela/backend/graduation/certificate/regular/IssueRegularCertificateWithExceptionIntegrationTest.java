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
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsUpdater;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionNotApprovedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionTargetMismatchException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionDueExpirer;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ApproveCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RequestCertificateExceptionUseCase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class IssueRegularCertificateWithExceptionIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANISM_REFERENCE = "EXP-77/2026";
    private static final String REQUEST_REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";
    private static final String DECISION_REASON = "Reclamo verificado con el expediente";

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
    private RequestCertificateExceptionUseCase requestUseCase;
    @Autowired
    private ApproveCertificateExceptionUseCase approveUseCase;
    @Autowired
    private CertificateExceptionDueExpirer dueExpirer;
    @Autowired
    private CertificateExceptionApprovalRepository exceptionRepository;
    @Autowired
    private RegularStudentCertificateRepository certificateRepository;

    private Long enrollmentId;
    private Long studentId;
    private Long sectionEnrollmentId;
    private int recordedDays;
    private Person staff;
    private Person director;
    private String staffToken;
    private String directorToken;
    private String otherStaffToken;

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
            director = persistPerson("DIRECTOR");
            staffToken = jwtService.generateAccessToken(staff);
            directorToken = jwtService.generateAccessToken(director);
            otherStaffToken = jwtService.generateAccessToken(persistPerson("ADMINISTRATIVO"));
        });
        recordedDays = 0;
        recordAttendance(8, 1);
        setBillingMode("FULL");
        addOverdueInstallment();
        actAs(staff);
    }

    @AfterEach
    void restoreBillingModeAndSecurity() {
        SecurityContextHolder.clearContext();
        jdbc.update("update regular_certificate_settings set validity_days = 30, exception_validity_days = 7, evidence_required = false");
        setBillingMode("FULL");
    }

    private void actAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
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
        person.setUsername("exception." + suffix);
        person.setEmail("exception." + suffix + "@example.com");
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
            var instructor = new CycleTestFixtures(entityManager).instructor();
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

    private CertificateExceptionResponse requestException() {
        actAs(staff);
        return requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.EDUCATION_MINISTRY, ORGANISM_REFERENCE, REQUEST_REASON));
    }

    private Long approvedExceptionId() {
        Long id = requestException().id();
        actAs(director);
        approveUseCase.approve(id, DECISION_REASON);
        actAs(staff);
        return id;
    }

    private RegularCertificateResponse issueWith(Long exceptionId) {
        return issueUseCase.issue(new IssueRegularCertificateRequest(RegularityKind.PROGRAM, enrollmentId, false, null, exceptionId));
    }

    private String exceptionStatus(Long id) {
        return jdbc.queryForObject("select status from certificate_exception_approval where id = ?", String.class, id);
    }

    private Long newEnrollmentOfAnotherStudent() {
        return transactions.execute(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(entityManager.find(Student.class, studentId).getInstitution());
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(fixtures.program());
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            entityManager.flush();
            return enrollment.getId();
        });
    }

    @Test
    void approvedExceptionLetsTheStudentWithDebtGetTheCertificateAndIsConsumedExactlyOnce() throws Exception {
        Long exceptionId = approvedExceptionId();

        RegularCertificateResponse response = issueWith(exceptionId);

        assertThat(response.status()).isEqualTo(RegularCertificateStatus.VALID);
        assertThat(response.standingBasis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
        assertThat(exceptionStatus(exceptionId)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("select consumed_certificate_id from certificate_exception_approval where id = ?", Long.class, exceptionId))
                .isEqualTo(response.id());
        assertThat(jdbc.queryForObject("select exception_id from regular_student_certificate where id = ?", Long.class, response.id()))
                .isEqualTo(exceptionId);
        assertThat(jdbc.queryForObject("select count(*) from certificate_exception_status_change where approval_id = ? and event = 'CONSUME'",
                Integer.class, exceptionId)).isEqualTo(1);
        RegularCertificatePdf pdf = pdfUseCase.pdfOf(response.id());
        try (var document = Loader.loadPDF(pdf.content())) {
            assertThat(new PDFTextStripper().getText(document).toLowerCase()).doesNotContain("excepci").doesNotContain("reclamo");
        }
    }

    @Test
    void exceptionDoesNotSkipTheAcademicRegularityCheck() {
        Long exceptionId = approvedExceptionId();
        recordAttendance(0, 10);

        assertThatThrownBy(() -> issueWith(exceptionId)).isInstanceOf(NotRegularException.class);

        assertThat(exceptionStatus(exceptionId)).isEqualTo("APPROVED");
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void expiredExceptionIsRefusedAndNoCertificateIsLeftBehind() {
        Long exceptionId = approvedExceptionId();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0).plusDays(7));

        assertThatThrownBy(() -> issueWith(exceptionId)).isInstanceOf(ExceptionNotApprovedException.class);

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
        assertThat(exceptionStatus(exceptionId)).isEqualTo("APPROVED");
        assertThat(dueExpirer.expireIfDue(exceptionId)).isTrue();
        assertThat(exceptionStatus(exceptionId)).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> issueWith(exceptionId)).isInstanceOf(ExceptionNotApprovedException.class);
    }

    @Test
    void consumedExceptionCannotBeUsedAgainOnceTheFirstCertificateExpired() {
        Long exceptionId = approvedExceptionId();
        issueWith(exceptionId);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0).plusDays(31));

        assertThatThrownBy(() -> issueWith(exceptionId)).isInstanceOf(ExceptionAlreadyConsumedException.class);

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
        assertThat(exceptionStatus(exceptionId)).isEqualTo("CONSUMED");
    }

    @Test
    void exceptionOfAnotherEnrollmentIsRefusedAsTargetMismatch() {
        Long otherEnrollmentId = newEnrollmentOfAnotherStudent();
        setBillingMode("OFF");
        actAs(staff);
        Long foreignException = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, otherEnrollmentId,
                OrganismKind.COURT, "Causa 12/2026", REQUEST_REASON)).id();
        actAs(director);
        approveUseCase.approve(foreignException, DECISION_REASON);
        actAs(staff);
        setBillingMode("FULL");

        assertThatThrownBy(() -> issueWith(foreignException)).isInstanceOf(ExceptionTargetMismatchException.class);

        assertThat(exceptionStatus(foreignException)).isEqualTo("APPROVED");
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void pendingOrUnknownExceptionIsRefused() {
        Long pending = requestException().id();

        assertThatThrownBy(() -> issueWith(pending)).isInstanceOf(ExceptionNotApprovedException.class);
        assertThatThrownBy(() -> issueWith(987654321L)).isInstanceOf(CertificateExceptionNotFoundException.class);
    }

    @Test
    void fullModeWithoutDebtRefusesTheRequestAndTheIssuanceWithNoDebtToExcept() {
        Long exceptionId = approvedExceptionId();
        jdbc.update("update invoice set due_date = date '2027-01-01'");

        assertThatThrownBy(() -> issueWith(exceptionId)).isInstanceOf(NoDebtToExceptException.class);
        assertThat(exceptionStatus(exceptionId)).isEqualTo("APPROVED");
        jdbc.update("update certificate_exception_approval set status = 'WITHDRAWN' where id = ?", exceptionId);
        assertThatThrownBy(this::requestException).isInstanceOf(NoDebtToExceptException.class);
    }

    @Test
    void billingOffRegistersTheRequestWithoutCheckingForDebt() {
        setBillingMode("OFF");

        assertThat(requestException().status()).isEqualTo(CertificateExceptionStatus.PENDING);
    }

    @Test
    void twoConcurrentIssuancesWithTheSameExceptionLetExactlyOneWin() throws Exception {
        Long exceptionId = approvedExceptionId();
        int threads = 2;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Throwable>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Throwable> attempt = () -> {
                    actAs(staff);
                    barrier.await();
                    try {
                        issueWith(exceptionId);
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                futures.add(executor.submit(attempt));
            }
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get());
            }

            assertThat(outcomes.stream().filter(outcome -> outcome == null)).hasSize(1);
            assertThat(outcomes.stream().filter(outcome -> outcome != null)).allSatisfy(outcome -> assertThat(outcome)
                    .isInstanceOfAny(ConcurrentRegularCertificateIssueException.class, ExceptionAlreadyConsumedException.class,
                            RegularCertificateAlreadyIssuedException.class));
            assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
            assertThat(exceptionStatus(exceptionId)).isEqualTo("CONSUMED");
            assertThat(jdbc.queryForObject("select count(*) from certificate_exception_status_change where approval_id = ? and event = 'CONSUME'",
                    Integer.class, exceptionId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void endpointsFlowRequestApproveAndIssueWithTheExceptionId() throws Exception {
        String body = "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + ",\"organismKind\":\"COURT\",\"organismReference\":\"Causa 9\","
                + "\"reason\":\"" + REQUEST_REASON + "\"}";
        String created = mockMvc.perform(post("/certificate-exceptions").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/certificate-exceptions/" + id + "/approve").header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + DECISION_REASON + "\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/certificate-exceptions/" + id + "/approve").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + DECISION_REASON + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + ",\"standingConfirmed\":false,\"exceptionId\":" + id + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.standingBasis").value("EXCEPTION_APPROVED"));
        mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + ",\"standingConfirmed\":false,\"exceptionId\":" + id + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/certificate-exceptions/" + id).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONSUMED"));
    }

    @Test
    void listingFiltersByStudentAndStatusInTheQuery() throws Exception {
        Long id = requestException().id();

        mockMvc.perform(get("/certificate-exceptions").param("studentId", studentId.toString()).param("status", "PENDING")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
        mockMvc.perform(get("/certificate-exceptions").param("status", "APPROVED").param("studentId", studentId.toString())
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());
        mockMvc.perform(get("/certificate-exceptions").param("studentId", "987654321")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());
    }

    @Test
    void listingShowsEverythingToTheDirectorAndOnlyTheirOwnToAdministrative() throws Exception {
        Long id = requestException().id();

        mockMvc.perform(get("/certificate-exceptions").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
        mockMvc.perform(get("/certificate-exceptions").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
        mockMvc.perform(get("/certificate-exceptions").header("Authorization", "Bearer " + otherStaffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());
        mockMvc.perform(get("/certificate-exceptions/" + id).header("Authorization", "Bearer " + otherStaffToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/certificate-exceptions/" + id + "/withdraw").header("Authorization", "Bearer " + otherStaffToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/certificate-exceptions/" + id + "/withdraw").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/certificate-exceptions/" + id + "/withdraw").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    private String directBody(Long forEnrollmentId, String reason) {
        return "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + forEnrollmentId + ",\"organismKind\":\"COURT\",\"organismReference\":\"Causa 77\","
                + "\"reason\":\"" + reason + "\"}";
    }

    @Test
    void directorCreatesAnApprovedExceptionThatIsAuditedAndConsumedByTheIssuance() throws Exception {
        String created = mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, REQUEST_REASON)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdByDirector").value(true)).andExpect(jsonPath("$.decidedById").value(director.getId()))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CERTIFICATE_EXCEPTION_DIRECT_CREATED' and entity_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from certificate_exception_status_change where approval_id = ? and event = 'DIRECT_APPROVE'",
                Integer.class, id)).isEqualTo(1);
        actAs(staff);
        assertThat(issueWith(id).standingBasis()).isEqualTo(StandingBasis.EXCEPTION_APPROVED);
        assertThat(exceptionStatus(id)).isEqualTo("CONSUMED");
        assertThatThrownBy(() -> issueWith(id)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void directExceptionDoesNotSkipTheAcademicRegularityCheck() throws Exception {
        String created = mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, REQUEST_REASON)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        recordAttendance(0, 10);
        actAs(staff);

        assertThatThrownBy(() -> issueWith(id)).isInstanceOf(NotRegularException.class);

        assertThat(exceptionStatus(id)).isEqualTo("APPROVED");
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void administrativeCannotCreateADirectException() throws Exception {
        mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, REQUEST_REASON)))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from certificate_exception_approval where program_enrollment_id = ?", Integer.class,
                enrollmentId)).isZero();
    }

    @Test
    void directCreationWithAShortReasonIsABadRequest() throws Exception {
        mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, "motivo corto")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void directCreationInFullBillingWithoutDebtIsRefusedWithNoDebtToExcept() throws Exception {
        Long settledEnrollmentId = newEnrollmentOfAnotherStudent();

        mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(directBody(settledEnrollmentId, REQUEST_REASON)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_DEBT_TO_EXCEPT"));
    }

    @Test
    void directExceptionExpiresWithTheClockAndASecondOpenOneIsRefused() throws Exception {
        mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, REQUEST_REASON))).andExpect(status().isCreated());
        mockMvc.perform(post("/certificate-exceptions/direct").header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(directBody(enrollmentId, REQUEST_REASON))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_EXCEPTION_ALREADY_OPEN"));
        Long id = jdbc.queryForObject("select id from certificate_exception_approval where program_enrollment_id = ?", Long.class, enrollmentId);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0).plusDays(7));

        assertThat(dueExpirer.expireIfDue(id)).isTrue();
        assertThat(exceptionStatus(id)).isEqualTo("EXPIRED");
    }

    @Test
    void databaseRefusesADirectorCreationWhoseDeciderIsNotTheRequester() {
        Long id = approvedExceptionId();

        assertThatThrownBy(() -> jdbc.update("update certificate_exception_approval set created_by_director = true where id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
