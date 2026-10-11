package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.AdmissionEnrollmentStalledNotifier;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class ProgramEnrollmentAdmissionPaymentIT extends AbstractIntegrationTest {
    @Autowired private RequestProgramEnrollmentApplicationUseCase requestUseCase;
    @Autowired private ApproveProgramEnrollmentApplicationUseCase approveUseCase;
    @Autowired private RegisterPaymentUseCase registerPaymentUseCase;
    @Autowired private ProgramEnrollmentApplicationRepository applicationRepository;
    @Autowired private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private AdmissionEnrollmentStalledNotifier stalledNotifier;
    @Autowired private NotificationRepository notificationRepository;
    @MockitoSpyBean private ProgramEnrollmentService programEnrollmentService;

    private BillingInvariantChecker invariants;

    @BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        reset(programEnrollmentService);
    }

    private Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Person persistPreceptor() {
        return transactionTemplate.execute(status -> {
            Person preceptor = persistPerson("PREC" + System.nanoTime());
            preceptor.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
            return preceptor;
        });
    }

    private ProgramEnrollmentApplication requestApplication() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("9" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = new Program();
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            Person director = persistPerson("DIR" + System.nanoTime());
            Term term = new Term();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(director);
            entityManager.persist(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula de prueba", ars(BigDecimal.valueOf(50000)), LocalDate.now().minusDays(1), director.getId());
            entityManager.flush();
            return requestUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));
        });
    }

    private Invoice approve(ProgramEnrollmentApplication application) {
        Person preceptor = persistPreceptor();
        return transactionTemplate.execute(status -> {
            ProgramEnrollmentApplication approved = approveUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));
            return approved.getAdmissionInvoice();
        });
    }

    private void payInFull(Invoice invoice) {
        Person payer = persistPreceptor();
        transactionTemplate.executeWithoutResult(status -> {
            Invoice managed = invoiceRepository.findById(invoice.getId()).orElseThrow();
            registerPaymentUseCase.register(new RegisterPaymentRequest(
                    List.of(new PaymentAllocationRequest(managed.getId(), managed.getAmounts().outstandingAmount().value())),
                    PaymentMethod.TRANSFER, false, null, null, null, null), payer);
        });
    }

    private boolean isEnrolled(ProgramEnrollmentApplication application) {
        return !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                application.getStudent().getId(), application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED)).isEmpty();
    }

    @Test
    @Tag("D-227")
    void payingTheAdmissionInvoiceEnrollsTheStudentAfterTheCommit() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                application.getStudent().getId(), application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED)))
                .singleElement().satisfies(enrollment -> {
                    java.util.Optional<Long> linkedEnrollmentId = transactionTemplate.execute(status ->
                            invoiceRepository.findById(invoice.getId()).orElseThrow().findProgramEnrollmentId());
                    assertThat(linkedEnrollmentId).contains(enrollment.getId());
                });
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-227")
    void aFailingEnrollmentAfterThePaymentDoesNotRollBackThePayment() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        failEnrollmentTechnically();

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoice)).isEqualTo(1L));
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(isEnrolled(application)).isFalse();
        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
    }

    @Test
    @Tag("D-184")
    void approvingNotifiesTheStudentToPayTheAdmissionFeeLinkedToTheApplicationAfterTheCommit() {
        ProgramEnrollmentApplication application = requestApplication();

        approve(application);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(application.getStudent().getPerson().getId()))
                        .singleElement().satisfies(notification -> {
                            assertThat(notification.getType()).isEqualTo(NotificationType.PROGRAM_APPLICATION_APPROVED);
                            assertThat(notification.getRelatedEntityType()).isEqualTo("ProgramEnrollmentApplication");
                            assertThat(notification.getRelatedEntityId()).isEqualTo(application.getId());
                        }));
    }

    @Test
    @Tag("D-232")
    void aStudentWhoBecameInactiveAndNotConfirmedBetweenApprovalAndPaymentIsEnrolledAnyway() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        jdbc.update("update student set onboarding_status = ? where id = ?", StudentOnboardingStatus.PROSPECTO.name(), application.getStudent().getId());
        jdbc.update("update person set status = ? where id = ?", StatusType.INACTIVE.name(), application.getStudent().getPerson().getId());

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-232")
    void aTechnicalFailureLastingMoreThanAnHourNotifiesTheSecretariatExactlyOnce() {
        Person staff = persistPreceptor();
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        failEnrollmentTechnically();
        payInFull(invoice);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoice)).isEqualTo(1L));

        stalledNotifier.notifySecretariatOfStalledEnrollments();
        assertThat(stalledNotificationsFor(staff, application)).isZero();

        MutableClock.INSTANCE.advance(Duration.ofMinutes(61));
        stalledNotifier.notifySecretariatOfStalledEnrollments();
        stalledNotifier.notifySecretariatOfStalledEnrollments();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stalledNotificationsFor(staff, application)).isEqualTo(1L));
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(stalledNotificationsFor(staff, application)).isEqualTo(1L));
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(application.getStudent().getPerson().getId()))
                .noneMatch(notification -> notification.getType() == NotificationType.PROGRAM_ENROLLMENT_STALLED);
    }

    @Test
    @Tag("D-232")
    void anEnrollmentThatSucceedsNeverNotifiesTheSecretariat() {
        Person staff = persistPreceptor();
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        payInFull(invoice);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());

        MutableClock.INSTANCE.advance(Duration.ofHours(2));
        stalledNotifier.notifySecretariatOfStalledEnrollments();

        assertThat(stalledNotificationsFor(staff, application)).isZero();
    }

    @Test
    @Tag("D-231")
    void aRedeliveredPaymentEventDoesNotDuplicateTheEnrollment() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        payInFull(invoice);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());

        publishPaymentEvent(invoice);
        publishPaymentEvent(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoice)).isZero());
        assertThat(activeEnrollmentsOf(application)).hasSize(1);
    }

    @Test
    @Tag("D-231")
    void concurrentDeliveriesOfThePaymentEventEnrollOnceAndEveryDeliveryCompletesWithoutError() throws Exception {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> deliveries = new java.util.ArrayList<>();
        for (int delivery = 0; delivery < 3; delivery++) {
            deliveries.add(executor.submit(() -> {
                start.await();
                publishPaymentEvent(invoice);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> delivery : deliveries) {
            delivery.get(60, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoice)).isZero());
        assertThat(activeEnrollmentsOf(application)).hasSize(1);
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isPresent();
    }

    @Test
    @Tag("D-231")
    void theDatabaseRejectsASecondEnrollmentForTheSameAdmissionApplication() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        payInFull(invoice);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        Long enrollmentId = programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId()).orElseThrow();

        assertThatThrownBy(() -> jdbc.update("""
                insert into program_enrollment (student_id, program_id, status, payment_status, admission_application_id, version)
                select student_id, program_id, 'PAUSED', payment_status, admission_application_id, 0 from program_enrollment where id = ?
                """, enrollmentId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-231")
    void theEnrollmentCreatedAfterThePaymentIsLinkedToTheApplication() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isEmpty();

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isPresent());
    }

    private void failEnrollmentTechnically() {
        doThrow(new IllegalStateException("simulated technical failure")).when(programEnrollmentService).enrollApplicantAfterAdmissionPayment(any());
    }

    private void publishPaymentEvent(Invoice invoice) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(AdmissionInvoicePaidEvent.paidFor(invoice.getId())));
    }

    private List<ProgramEnrollment> activeEnrollmentsOf(ProgramEnrollmentApplication application) {
        return programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(application.getStudent().getId(), application.getProgram().getId(),
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
    }

    private long incompletePublicationsOf(Invoice invoice) {
        return jdbc.queryForObject("""
                select count(*) from event_publication
                where event_type = ? and (serialized_event::jsonb ->> 'invoiceId')::bigint = ? and completion_date is null
                """, Long.class, AdmissionInvoicePaidEvent.class.getName(), invoice.getId());
    }

    private long stalledNotificationsFor(Person staff, ProgramEnrollmentApplication application) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(staff.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PROGRAM_ENROLLMENT_STALLED
                        && application.getId().equals(notification.getRelatedEntityId())).count();
    }

    @Test
    @Tag("D-229")
    void payingAnOverdueAdmissionInvoiceStillEnrollsWhileTheApplicationStaysApproved() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        BillingTestFixtures.markInvoiceAsOverdueOn(invoice, LocalDate.now().plusDays(11));
        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        invariants.assertAllBillingInvariantsHold();
    }

    @Disabled("CI-IT: conflicto D-230 vs orden de imputación V2 (pago de admisión imputado a deuda vencida más vieja) — pendiente de decisión de negocio, deuda técnica #44")
    @Test
    @Tag("D-230")
    void aDebtAppearingAfterTheApprovalDoesNotBlockTheEnrollment() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        Invoice otherDebt = BillingTestFixtures.invoice().forStudent(application.getStudent()).ofType(FeeType.ADMINISTRATIVE_FEE)
                .dueOn(LocalDate.now().minusDays(5)).overdue().build();

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        assertThatInvoice(invoiceRepository.findById(otherDebt.getId()).orElseThrow()).isOverdue();
        invariants.assertAllBillingInvariantsHold();
    }
}
