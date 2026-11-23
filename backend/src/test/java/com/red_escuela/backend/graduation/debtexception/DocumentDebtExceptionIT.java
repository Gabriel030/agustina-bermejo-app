package com.red_escuela.backend.graduation.debtexception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.api.DebtExceptionGate;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionExpirer;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DocumentDebtExceptionIT extends AbstractIntegrationTest {

    private static final String REASON = "Convenio de pago firmado con la familia del alumno";

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
    private DebtExceptionGate gate;
    @Autowired
    private DebtExceptionExpirer expirer;

    private Long studentId;
    private Long otherStudentId;
    private Person administrative;
    private String administrativeToken;
    private String directorToken;
    private String subdirectorToken;
    private String studentToken;

    @BeforeEach
    void seedStudentsAndStaff() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        transactions.executeWithoutResult(status -> {
            studentId = persistStudent().getId();
            otherStudentId = persistStudent().getId();
            administrative = persistPerson("ADMINISTRATIVO");
            administrativeToken = jwtService.generateAccessToken(administrative);
            directorToken = jwtService.generateAccessToken(persistPerson("DIRECTOR"));
            subdirectorToken = jwtService.generateAccessToken(persistPerson("SUBDIRECTOR"));
            studentToken = jwtService.generateAccessToken(persistPerson("ALUMNO"));
        });
        actAs(administrative);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from document_debt_exception");
    }

    @Test
    void administrativeIssuesAnExceptionAndItIsAuditedWithoutTheReason() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.studentId").value(studentId));

        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        List<String> auditedReasons = jdbc.queryForList(
                "select coalesce(reason, '') || coalesce(before_value, '') || coalesce(after_value, '') from audit_log where action = 'DOCUMENT_DEBT_EXC_ISSUED' and entity_id = ?",
                String.class, id);
        assertThat(auditedReasons).hasSize(1);
        assertThat(auditedReasons.get(0)).doesNotContain(REASON).contains("REGULAR_STUDENT_CERTIFICATE");
    }

    @Test
    void directorCanIssueAnException() throws Exception {
        issue(directorToken, studentId, "EXAM_CERTIFICATE", REASON, 90).andExpect(status().isCreated());
    }

    @Test
    void subdirectorIsDeniedIssuingAndListing() throws Exception {
        issue(subdirectorToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions").header("Authorization", "Bearer " + subdirectorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotIssueListOrDownloadExceptions() throws Exception {
        issue(studentToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/document-debt-exceptions/1/pdf").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reasonShorterThanTwentyRealCharactersIsRejected() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", "corto                    ", 5)
                .andExpect(status().isBadRequest());
    }

    @Test
    void validityOutsideOneToNinetyDaysIsRejected() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 0).andExpect(status().isBadRequest());
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 91).andExpect(status().isBadRequest());
    }

    @Test
    void unknownStudentIsRejected() throws Exception {
        issue(administrativeToken, 999999999L, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStudentAndStatus() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        issue(administrativeToken, otherStudentId, "EXAM_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        mockMvc.perform(get("/document-debt-exceptions").param("studentId", studentId.toString()).param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].studentId").value(studentId));
    }

    @Test
    void activeExceptionIsConsumedExactlyOnce() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        Optional<Long> first = consume(studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null);
        Optional<Long> second = consume(studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, first.get()))
                .isEqualTo("USED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", first.get())).isEqualTo(1);
    }

    @Test
    void exceptionIsOnlyConsumableByItsStudentAndDocumentType() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isCreated());

        assertThat(consume(otherStudentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null)).isPresent();
    }

    @Test
    void exceptionBoundToARegistrationOnlyServesThatRegistration() throws Exception {
        issueForRegistration(studentId, "EXAM_REGISTRATION_CERTIFICATE", 77L).andExpect(status().isCreated());

        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, null)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 78L)).isEmpty();
        assertThat(consume(studentId, CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, 77L)).isPresent();
    }

    @Test
    void expiredExceptionCannotBeConsumedAndExpiryIsAudited() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 1).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 11, 10, 0));

        assertThat(consume(studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null)).isEmpty();
        assertThat(expirer.expireDue()).isEqualTo(1);
        assertThat(expirer.expireDue()).isZero();
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, id)).isEqualTo("EXPIRED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_EXPIRED", id)).isEqualTo(1);
    }

    @Test
    void twoSimultaneousConsumptionsOnlyOneWins() throws Exception {
        issue(administrativeToken, studentId, "REGULAR_STUDENT_CERTIFICATE", REASON, 5).andExpect(status().isCreated());
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Optional<Long>> consumption = () -> {
            actAs(administrative);
            ready.countDown();
            go.await();
            try {
                return gate.consumeActiveException(studentId, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, null);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        List<Future<Optional<Long>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(consumption));
        }
        ready.await();
        go.countDown();
        int winners = 0;
        for (Future<Optional<Long>> future : futures) {
            winners += future.get().isPresent() ? 1 : 0;
        }
        executor.shutdown();

        assertThat(winners).isEqualTo(1);
        Long id = jdbc.queryForObject("select id from document_debt_exception", Long.class);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", id)).isEqualTo(1);
    }

    private Optional<Long> consume(Long student, CertificateDocumentType type, Long registrationId) {
        actAs(administrative);
        return gate.consumeActiveException(student, type, registrationId);
    }

    private int auditCount(String action, Long entityId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, entityId);
    }

    private ResultActions issue(String token, Long student, String documentType, String reason, int validityDays) throws Exception {
        return mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student + ",\"documentType\":\"" + documentType + "\",\"reason\":\"" + reason
                        + "\",\"validityDays\":" + validityDays + "}"));
    }

    private ResultActions issueForRegistration(Long student, String documentType, Long registrationId) throws Exception {
        return mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student + ",\"documentType\":\"" + documentType + "\",\"registrationId\":" + registrationId
                        + ",\"reason\":\"" + REASON + "\",\"validityDays\":5}"));
    }

    private void actAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Student persistStudent() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("ALUMNO"));
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
        person.setUsername("debtexc." + suffix);
        person.setEmail("debtexc." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }
}
