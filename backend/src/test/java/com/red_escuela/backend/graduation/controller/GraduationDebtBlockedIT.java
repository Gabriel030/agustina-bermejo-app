package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class GraduationDebtBlockedIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    private Person studentPerson;
    private ProgramEnrollment enrollment;
    private Long requestId;
    private String adminToken;
    private String studentToken;

    @BeforeEach
    void createRequestWithTwoOverdueInstallments() {
        transactions.executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            studentPerson = persistPerson(null);
            Student student = new Student();
            student.setPerson(studentPerson);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = new Program();
            program.setName("Carrera de egreso");
            program.setTotalYears(1);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);
            enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(enrollment);
            GraduationRequest request = new GraduationRequest();
            request.setProgramEnrollment(enrollment);
            request.setStatus(GraduationRequestStatus.REQUESTED);
            request.setRequestedAt(LocalDateTime.now());
            entityManager.persist(request);
            requestId = request.getId();
            addInstallment(YearMonth.of(2026, 3), 15000);
            addInstallment(YearMonth.of(2026, 4), 18000);
            adminToken = jwtService.generateAccessToken(persistPerson("ADMINISTRATIVO"));
            studentToken = jwtService.generateAccessToken(persistPerson("ALUMNO"));
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

    private void addInstallment(YearMonth period, long amount) {
        BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(period)
                .dueOn(period.atDay(10)).withBaseAmount(ars(amount)).build();
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("egress." + suffix);
        person.setEmail("egress." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        if (roleName != null) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            person.getRoles().add(role);
        }
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void verifyDebt() throws Exception {
        mockMvc.perform(post("/graduation-requests/" + requestId + "/verify-debt").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    private List<Notification> debtNotifications() {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(studentPerson.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.GRADUATION_REQUEST_DEBT_BLOCKED).toList();
    }

    private void awaitDebtNotifications(int expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(debtNotifications()).hasSize(expected));
    }

    private void assertStaysAt(int expected) {
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(debtNotifications()).hasSize(expected));
    }

    @Test
    void verifyingWithDebtReturnsTheAmountsInTheErrorAndTheNotificationSurvivesTheRollback() throws Exception {
        mockMvc.perform(post("/graduation-requests/" + requestId + "/verify-debt").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_DEBT"))
                .andExpect(jsonPath("$.details.totalDue").value(33000.0))
                .andExpect(jsonPath("$.details.installmentCount").value(2));

        assertThat(entityManager.find(GraduationRequest.class, requestId).getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
        awaitDebtNotifications(1);
        Notification notification = debtNotifications().get(0);
        assertThat(notification.getMessage()).startsWith("Tu solicitud de egreso está detenida por una deuda pendiente de $33.000,00 (2 cuotas).")
                .contains("/alumno/pagos").doesNotContain("administración");
        assertThat(notification.getRelatedEntityId()).isEqualTo(requestId);
    }

    @Test
    void withBillingInMirrorModeTheNoticeSendsTheStudentToAdministrationWithoutPaymentLink() throws Exception {
        setBillingMode("MIRROR");

        verifyDebt();

        awaitDebtNotifications(1);
        assertThat(debtNotifications().get(0).getMessage()).contains("administración").doesNotContain("/alumno/pagos");
    }

    @Test
    void theSameDebtDoesNotNotifyAgainWithinADayButDoesAfterADayOrWhenTheAmountChanges() throws Exception {
        verifyDebt();
        awaitDebtNotifications(1);

        verifyDebt();
        assertStaysAt(1);

        jdbc.update("update notification set created_at = created_at - interval '25 hours' where related_entity_id = ? and type = 'GRADUATION_REQUEST_DEBT_BLOCKED'", requestId);
        verifyDebt();
        awaitDebtNotifications(2);

        verifyDebt();
        assertStaysAt(2);

        transactions.executeWithoutResult(status -> addInstallment(YearMonth.of(2026, 5), 20000));
        verifyDebt();
        awaitDebtNotifications(3);
        assertThat(debtNotifications().get(0).getMessage()).contains("$53.000,00 (3 cuotas)");
    }

    @Test
    void debtSummaryListsTheUnpaidInstallmentsForStaffAndIsForbiddenToStudents() throws Exception {
        mockMvc.perform(get("/graduation-requests/" + requestId + "/debt-summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustCheckDebt").value(true))
                .andExpect(jsonPath("$.totalDue.amount").value(33000.0))
                .andExpect(jsonPath("$.installments.length()").value(2))
                .andExpect(jsonPath("$.installments[0].period").value("2026-03"))
                .andExpect(jsonPath("$.installments[0].dueDate").value("2026-03-10"))
                .andExpect(jsonPath("$.installments[0].amount.amount").value(15000.0))
                .andExpect(jsonPath("$.installments[1].amount.amount").value(18000.0));

        mockMvc.perform(get("/graduation-requests/" + requestId + "/debt-summary").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void debtSummaryWithBillingOffReportsNoDebtControl() throws Exception {
        setBillingMode("OFF");

        mockMvc.perform(get("/graduation-requests/" + requestId + "/debt-summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustCheckDebt").value(false))
                .andExpect(jsonPath("$.totalDue.amount").value(0.0))
                .andExpect(jsonPath("$.installments.length()").value(0));
    }
}
