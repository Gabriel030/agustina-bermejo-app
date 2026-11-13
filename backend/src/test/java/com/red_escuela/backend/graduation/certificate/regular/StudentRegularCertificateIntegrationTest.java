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
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.events.RegularCertificateRequestFulfilledEvent;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
@RecordApplicationEvents
class StudentRegularCertificateIntegrationTest extends AbstractIntegrationTest {

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
    private RegularStudentCertificateRepository certificateRepository;
    @Autowired
    private RegularCertificateRequestRepository requestRepository;
    @Autowired
    private ApplicationEvents events;

    private Long enrollmentId;
    private Long studentId;
    private Long sectionEnrollmentId;
    private int recordedDays;
    private String studentToken;
    private String strangerToken;
    private String staffToken;

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

    private ResultActions submit(String token) throws Exception {
        return mockMvc.perform(post("/me/regular-certificates").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + "}"));
    }

    private ResultActions state(String token) throws Exception {
        return mockMvc.perform(get("/me/regular-certificates").param("kind", "PROGRAM")
                .param("enrollmentId", enrollmentId.toString()).header("Authorization", "Bearer " + token));
    }

    private ResultActions staffIssues() throws Exception {
        return mockMvc.perform(post("/regular-certificates").header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + ",\"standingConfirmed\":true,\"standingEvidence\":\""
                        + EVIDENCE + "\"}"));
    }

    private long idOf(ResultActions result, String path) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return id.longValue();
    }

    private List<RegularCertificateRequest> requests() {
        return requestRepository.findByStudentIdOrderByRequestedAtDesc(studentId);
    }

    private void assertPendingRequestWithoutCertificate(String mode) throws Exception {
        setBillingMode(mode);

        submit(studentToken).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.request.outcome").value("PENDING_STAFF"))
                .andExpect(jsonPath("$.request.channel").value("STUDENT_PORTAL"))
                .andExpect(jsonPath("$.certificate").doesNotExist());

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
        state(studentToken).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.staffConfirmationRequired").value(true));
    }

    @Test
    void b101OffModeStudentRequestStaysPendingForStaff() throws Exception {
        assertPendingRequestWithoutCertificate("OFF");
    }

    @Test
    void b102MirrorModeStudentRequestStaysPendingForStaff() throws Exception {
        assertPendingRequestWithoutCertificate("MIRROR");
    }

    @Test
    void b103FullModeWithoutDebtIssuesImmediatelyAndStudentDownloadsThePdf() throws Exception {
        setBillingMode("FULL");

        ResultActions result = submit(studentToken).andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificate.status").value("VALID"))
                .andExpect(jsonPath("$.certificate.standingBasis").value("BILLING_VERIFIED"))
                .andExpect(jsonPath("$.request").doesNotExist());
        long id = idOf(result, "$.certificate.id");

        mockMvc.perform(get("/me/regular-certificates/" + id + "/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        state(studentToken).andExpect(jsonPath("$.state").value("VALID")).andExpect(jsonPath("$.certificate.id").value(id));
    }

    @Test
    void b104FullModeWithDebtIsUnavailableByPaymentsWithoutAmounts() throws Exception {
        setBillingMode("FULL");
        addOverdueInstallment();

        String body = state(studentToken).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableType").value("PAYMENTS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("amount").doesNotContain("15000");
        submit(studentToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_HAS_DEBT"));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).isEmpty();
    }

    @Test
    void b105StaffIssueClosesTheStudentPendingRequestInsteadOfCreatingAnother() throws Exception {
        submit(studentToken).andExpect(status().isAccepted());
        long pendingId = requests().get(0).getId();

        long certificateId = idOf(staffIssues().andExpect(status().isCreated()), "$.id");

        assertThat(requests()).hasSize(1);
        RegularCertificateRequest closed = requests().get(0);
        assertThat(closed.getId()).isEqualTo(pendingId);
        assertThat(closed.getOutcome()).isEqualTo(RegularRequestOutcome.ISSUED);
        assertThat(closed.getCertificateId()).isEqualTo(certificateId);
        assertThat(events.stream(RegularCertificateRequestFulfilledEvent.class).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.certificateId()).isEqualTo(certificateId);
                    assertThat(event.studentId()).isEqualTo(studentId);
                });
        state(studentToken).andExpect(jsonPath("$.state").value("VALID"));
        mockMvc.perform(get("/me/regular-certificates/" + certificateId + "/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void b106StaffIssueWithoutStudentRequestDoesNotNotifyAndKeepsTheSingleIssuedRequest() throws Exception {
        staffIssues().andExpect(status().isCreated());

        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome).containsExactly(RegularRequestOutcome.ISSUED);
        assertThat(events.stream(RegularCertificateRequestFulfilledEvent.class).toList()).isEmpty();
    }

    @Test
    void b107RepeatedSubmitReturnsTheSamePendingRequest() throws Exception {
        submit(studentToken).andExpect(status().isAccepted());
        submit(studentToken).andExpect(status().isAccepted());

        assertThat(requests()).hasSize(1);
    }

    @Test
    void b108OnlyOneValidCertificateEvenIfTheStudentAsksAgain() throws Exception {
        staffIssues().andExpect(status().isCreated());

        submit(studentToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGULAR_CERTIFICATE_ALREADY_ISSUED"));

        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from regular_student_certificate where status = 'VALID' and student_id = ?",
                Long.class, studentId)).isEqualTo(1L);
    }

    @Test
    void b109ExpiredCertificateIsNotDownloadableOnTheFirstMinuteAfterExpiryAndTheStudentCanRequestAgain() throws Exception {
        long id = idOf(staffIssues().andExpect(status().isCreated()), "$.id");

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 10, 10, 0));
        mockMvc.perform(get("/me/regular-certificates/" + id + "/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 10, 10, 1));
        mockMvc.perform(get("/me/regular-certificates/" + id + "/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CERTIFICATE_EXPIRED"));
        state(studentToken).andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.history[0].status").value("EXPIRED"));
        submit(studentToken).andExpect(status().isAccepted());
    }

    @Test
    void b110StudentWithAbsencesAboveTheLimitIsNotRegularByAttendance() throws Exception {
        recordAttendance(0, 8);

        state(studentToken).andExpect(jsonPath("$.state").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableType").value("ATTENDANCE"))
                .andExpect(jsonPath("$.reasons[0]").value("FREE_IN_ACTIVE_SECTION"));
        submit(studentToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REGULAR"));

        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome).containsExactly(RegularRequestOutcome.NOT_REGULAR);
    }

    @Test
    void b111CancelPendingRequestAndStaffIssueDoesNotReuseTheCancelledOne() throws Exception {
        submit(studentToken).andExpect(status().isAccepted());
        long requestId = requests().get(0).getId();

        mockMvc.perform(delete("/me/regular-certificate-requests/" + requestId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        RegularCertificateRequest cancelled = requestRepository.findById(requestId).orElseThrow();
        assertThat(cancelled.wasCancelledByStudent()).isTrue();
        state(studentToken).andExpect(jsonPath("$.state").value("AVAILABLE"));
        mockMvc.perform(delete("/me/regular-certificate-requests/" + requestId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGULAR_CERTIFICATE_REQUEST_NOT_PENDING"));
        staffIssues().andExpect(status().isCreated());
        assertThat(requestRepository.findById(requestId).orElseThrow().wasCancelledByStudent()).isTrue();
        assertThat(requests()).extracting(RegularCertificateRequest::getOutcome)
                .containsExactlyInAnyOrder(RegularRequestOutcome.REJECTED, RegularRequestOutcome.ISSUED);
    }

    @Test
    void b112AnotherStudentCannotSeeRequestDownloadOrCancelWhatIsNotTheirs() throws Exception {
        long certificateId = idOf(staffIssues().andExpect(status().isCreated()), "$.id");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 12, 11, 10, 0));
        submit(studentToken).andExpect(status().isAccepted());
        long requestId = requests().stream().filter(RegularCertificateRequest::isPending).findFirst().orElseThrow().getId();

        submit(strangerToken).andExpect(status().isNotFound());
        state(strangerToken).andExpect(status().isNotFound());
        mockMvc.perform(get("/me/regular-certificates/" + certificateId + "/pdf").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/me/regular-certificate-requests/" + requestId).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        assertThat(requestRepository.findById(requestId).orElseThrow().isPending()).isTrue();
        assertThat(requests()).filteredOn(RegularCertificateRequest::isPending).hasSize(1);
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
    }

    @Test
    void b113StaffCannotUseTheStudentEndpoints() throws Exception {
        submit(staffToken).andExpect(status().is4xxClientError());
        assertThat(requests()).isEmpty();
    }

    @Test
    void b114SubmissionsArePerStudentRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            submit(studentToken).andExpect(status().isAccepted());
        }

        submit(studentToken).andExpect(status().isTooManyRequests());
    }
}
