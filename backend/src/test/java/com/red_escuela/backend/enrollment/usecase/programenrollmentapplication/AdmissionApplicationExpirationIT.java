package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.domain.AccountTestData;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceTestData;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.AdmissionApplicationExpiration;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SharedSpyBeans;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
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

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@SharedSpyBeans
@Tag("D-229")
@Tag("D-252")
class AdmissionApplicationExpirationIT extends AbstractIntegrationTest {
    private static final LocalDate ADMISSION_END = LocalDate.now().plusDays(5);

    @Autowired private RequestProgramEnrollmentApplicationUseCase requestUseCase;
    @Autowired private ApproveProgramEnrollmentApplicationUseCase approveUseCase;
    @Autowired private ProgramEnrollmentApplicationRepository applicationRepository;
    @Autowired private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdmissionApplicationExpiration expiration;
    @Autowired private StudentCurrentAccountService accountService;
    @Autowired private UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    @Autowired private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    @Autowired private RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;

    private BillingInvariantChecker invariants;

    @BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
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
            cohort.setAdmissionEnd(ADMISSION_END);
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula de prueba",
                    ars(BigDecimal.valueOf(50000)), LocalDate.now().minusDays(1), director.getId());
            entityManager.flush();
            return requestUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));
        });
    }

    private Invoice approve(ProgramEnrollmentApplication application) {
        Person preceptor = persistPreceptor();
        return transactionTemplate.execute(status -> approveUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor)).getAdmissionInvoice());
    }

    private void payInFull(Invoice invoice) {
        BillingTestFixtures.registerApprovedBankTransferPayment(invoice.getId(), persistPreceptor().getId());
    }

    private BankTransferReceipt uploadReceipt(Invoice invoice, ProgramEnrollmentApplication application) {
        return uploadPaymentReceiptUseCase.upload(invoice.getId(), application.getStudent().getId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes()));
    }

    private ProgramEnrollmentApplicationStatus statusOf(ProgramEnrollmentApplication application) {
        return applicationRepository.findById(application.getId()).orElseThrow().getStatus();
    }

    private String paymentStatusOf(Invoice invoice) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoice.getId());
    }

    private boolean isEnrolled(ProgramEnrollmentApplication application) {
        return !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                application.getStudent().getId(), application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED)).isEmpty();
    }

    private Long accountIdOf(Invoice invoice) {
        return jdbc.queryForObject("select student_current_account_id from invoice where id = ?", Long.class, invoice.getId());
    }

    @Test
    @Tag("C-27")
    void theApplicationExpiresOnlyTheDayAfterTheAdmissionClosesAndTheInvoiceIsCancelledWithAnAutomaticCreditNote() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request", Long.class);

        MutableClock.INSTANCE.setDate(ADMISSION_END);
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(paymentStatusOf(invoice)).isNotEqualTo("CANCELLED");

        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isTrue();

        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.EXPIRED);
        assertThat(jdbc.queryForObject("select expired_at from program_enrollment_application where id = ?", java.sql.Timestamp.class, application.getId())).isNotNull();
        assertThat(paymentStatusOf(invoice)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForMap("select issued_by_process, issued_by_person_id, authorization_request_id, reason from credit_note where invoice_id = ?", invoice.getId()))
                .containsEntry("issued_by_process", "ADMISSION_EXPIRATION").containsEntry("issued_by_person_id", null)
                .containsEntry("authorization_request_id", null).containsEntry("reason", "CANCELLATION");
        assertThat(jdbc.queryForObject("select count(*) from authorization_request", Long.class)).isEqualTo(requestsBefore);
        assertThat(jdbc.queryForMap("select balance_amount, unallocated_credit_amount from student_current_account where id = ?", accountIdOf(invoice)))
                .satisfies(row -> assertThat(new BigDecimal(row.get("balance_amount").toString())).isEqualByComparingTo("0.00"))
                .satisfies(row -> assertThat(new BigDecimal(row.get("unallocated_credit_amount").toString())).isEqualByComparingTo("0.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-28")
    void aSecondRunLeavesASingleCreditNoteAndASingleStateChange() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());
        Object expiredAt = jdbc.queryForObject("select expired_at from program_enrollment_application where id = ?", Object.class, application.getId());

        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();

        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoice.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select expired_at from program_enrollment_application where id = ?", Object.class, application.getId())).isEqualTo(expiredAt);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-28")
    void twoConcurrentRunsExpireTheApplicationExactlyOnce() throws Exception {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> runs = List.of(
                executor.submit(() -> {
                    start.await();
                    return expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());
                }),
                executor.submit(() -> {
                    start.await();
                    return expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());
                }));
        start.countDown();
        long expired = 0;
        for (Future<Boolean> run : runs) {
            if (run.get(60, TimeUnit.SECONDS)) {
                expired++;
            }
        }
        executor.shutdownNow();

        assertThat(expired).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoice.getId())).isEqualTo(1L);
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.EXPIRED);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-26")
    void anOverdueAdmissionInvoicePaidWhileTheAdmissionIsOpenEnrollsAndNeverExpires() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END);
        jdbc.update("update invoice set due_date = ?, payment_status = 'OVERDUE' where id = ?", ADMISSION_END.minusDays(4), invoice.getId());

        payInFull(invoice);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(paymentStatusOf(invoice)).isEqualTo("PAID");
    }

    @Test
    @Tag("C-29")
    void aReceiptUnderReviewTheDayOfTheClosingIsLeftOutAndEnrollsWhenApprovedAfterTheProcessRan() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END);
        BankTransferReceipt receipt = uploadReceipt(invoice, application);

        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(paymentStatusOf(invoice)).isNotEqualTo("CANCELLED");

        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(3));
        approvePaymentReceiptUseCase.approve(receipt.getId(), persistPreceptor());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(isEnrolled(application)).isTrue());
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoice.getId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-29")
    void anInvoiceUnderReviewIsLeftOutOfTheExpiryUntilTheReviewIsReleased() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(invoice.getId()).orElseThrow()
                .markPaymentUnderReview(ADMISSION_END, InvoiceTestData.ACTOR, "Comprobante subido"));
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));

        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();
        assertThat(paymentStatusOf(invoice)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);

        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(invoice.getId()).orElseThrow()
                .releasePaymentReviewWithoutLateTerms(ADMISSION_END.plusDays(1), InvoiceTestData.ACTOR, "Comprobante rechazado"));

        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isTrue();
        assertThat(paymentStatusOf(invoice)).isEqualTo("CANCELLED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-29")
    void aRejectedReceiptOfAClosedAdmissionExpiresTheApplicationOnTheNextRun() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END);
        BankTransferReceipt receipt = uploadReceipt(invoice, application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isFalse();

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "Comprobante ilegible", persistPreceptor());
        assertThat(expiration.expireWhenAdmissionClosedAndUnpaid(application.getId())).isTrue();

        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.EXPIRED);
        assertThat(paymentStatusOf(invoice)).isEqualTo("CANCELLED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-29")
    void aPaymentAfterTheExpiryStaysAsCreditInFavorAndTheApplicationStaysExpired() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());
        Long accountId = accountIdOf(invoice);

        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(accountId),
                BillingTestFixtures.seedApprovedPaymentRow(accountId, AccountTestData.ars("50000.00")), AccountTestData.ars("50000.00"), ADMISSION_END.plusDays(1), "Pago tardío"));

        assertThat(new BigDecimal(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", String.class, accountId)))
                .isEqualByComparingTo("50000.00");
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.EXPIRED);
        assertThat(isEnrolled(application)).isFalse();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-29")
    void aReceiptUploadedAfterTheExpiryIsRefusedBecauseTheInvoiceIsCancelled() {
        ProgramEnrollmentApplication application = requestApplication();
        Invoice invoice = approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());

        assertThatThrownBy(() -> uploadReceipt(invoice, application)).isInstanceOf(RuntimeException.class);

        assertThat(paymentStatusOf(invoice)).isEqualTo("CANCELLED");
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.EXPIRED);
    }

    @Test
    @Tag("C-27")
    void theCandidateQuerySelectsApprovedApplicationsOnlyAfterTheClosingDay() {
        ProgramEnrollmentApplication application = requestApplication();
        approve(application);

        assertThat(applicationRepository.findIdsOfApprovedWithAdmissionClosedBefore(ADMISSION_END)).doesNotContain(application.getId());
        assertThat(applicationRepository.findIdsOfApprovedWithAdmissionClosedBefore(ADMISSION_END.plusDays(1))).contains(application.getId());
    }

    @Test
    void anExpiredApplicationDoesNotBlockAnotherRequestForTheSameProgram() {
        ProgramEnrollmentApplication application = requestApplication();
        approve(application);
        MutableClock.INSTANCE.setDate(ADMISSION_END.plusDays(1));
        expiration.expireWhenAdmissionClosedAndUnpaid(application.getId());

        assertThat(applicationRepository.findByStudentIdAndProgramIdAndStatus(application.getStudent().getId(), application.getProgram().getId(),
                ProgramEnrollmentApplicationStatus.PENDING)).isEmpty();
        assertThat(applicationRepository.existsApprovedWithUnpaidAdmissionInvoice(application.getStudent().getId(), application.getProgram().getId())).isFalse();
    }
}
