package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

    private BillingInvariantChecker invariants;

    @BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
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
            template.setRecurringFeeInvoicesCount(10);
            template.setCreatedBy(director);
            entityManager.persist(template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(template);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            PriceRevision revision = new PriceRevision();
            revision.setProgram(program);
            revision.setFeeType(FeeType.ENROLLMENT_FEE);
            revision.setDescription("Matrícula de prueba");
            revision.setMonthlyAmount(ars(BigDecimal.valueOf(50000)));
            revision.setEffectiveFrom(LocalDate.now().minusDays(1));
            revision.setCreatedBy(director);
            entityManager.persist(revision);
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
        jdbc.update("update student set onboarding_status = ? where id = ?", StudentOnboardingStatus.PROSPECTO.name(), application.getStudent().getId());

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null and serialized_event like ?", Long.class,
                "%\"invoiceId\":" + invoice.getId() + "%")).isEqualTo(1L));
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(isEnrolled(application)).isFalse();
        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
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
