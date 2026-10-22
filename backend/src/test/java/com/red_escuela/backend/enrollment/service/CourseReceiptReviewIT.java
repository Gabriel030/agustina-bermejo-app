package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException;
import com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
class CourseReceiptReviewIT extends AbstractIntegrationTest {
    private static final LocalDate UPLOAD_DAY = LocalDate.of(2027, 3, 9);

    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private BillingOperationPipeline pipeline;
    @Autowired private SubmitBankTransferReceiptOperation submitOperation;
    @Autowired private ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired private RejectPaymentReceiptUseCase rejectReceipt;
    @Autowired private CourseEnrollmentAdmissionPaymentListener admissionPaymentListener;
    @Autowired private StudentCurrentAccountService accountService;
    @Autowired private PreviewCashPaymentUseCase previewCashPayment;
    @Autowired private RegisterCashPaymentUseCase registerCashPayment;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

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

    private Person persistStaff() {
        return transactionTemplate.execute(status -> {
            Person person = persistPerson("ADM" + System.nanoTime());
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return person;
        });
    }

    private Student persistStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("7" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student;
        });
    }

    private Course persistPricedCourse() {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2027);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars(BigDecimal.valueOf(15000)),
                    UPLOAD_DAY.minusDays(30), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }

    private MultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    private CourseEnrollmentApplication request(Student student, Course course) {
        MutableClock.INSTANCE.setDate(UPLOAD_DAY);
        return applicationService.requestWithPayment(student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile());
    }

    private record NewFlowRequest(CourseEnrollmentApplication application, Long receiptId, Long invoiceId) {
    }

    private NewFlowRequest requestWithTheNewFlowReceipt(Student student, Course course, String declaredAmount) {
        CourseEnrollmentApplication application = request(student, course);
        jdbc.update("update bank_transfer_receipt set status = 'REJECTED' where id = ?", application.getPaymentReceipt().getId());
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(student.getId(), java.util.List.of(application.getInvoiceId()), ars(declaredAmount),
                UPLOAD_DAY, "OP-" + System.nanoTime(), "0170099220000067797370", "Ana Pérez", "Banco Nación", "sha-" + System.nanoTime(),
                studentId -> "students/" + studentId + "/comprobantes_" + System.nanoTime() + ".pdf", "b26-" + System.nanoTime(), StatusChangeActor.ofPerson(student.getPerson().getId()));
        BankTransferReceiptSubmissionResult result = ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) pipeline.runOperation(submitOperation, command)).result();
        return new NewFlowRequest(application, result.receiptId(), application.getInvoiceId());
    }

    private String invoiceStatus(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private boolean applicationCancelled(Long applicationId) {
        return jdbc.queryForObject("select cancelled_at is not null from course_enrollment_application where id = ?", Boolean.class, applicationId);
    }

    private String receiptStatus(Long receiptId) {
        return jdbc.queryForObject("select status from bank_transfer_receipt where id = ?", String.class, receiptId);
    }

    private BigDecimal creditAvailableIn(Long invoiceId) {
        return jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = (select student_current_account_id from invoice where id = ?)",
                BigDecimal.class, invoiceId);
    }

    private Long accountOf(Long invoiceId) {
        return jdbc.queryForObject("select student_current_account_id from invoice where id = ?", Long.class, invoiceId);
    }

    private void assertEnrollmentsStay(Student student, int expected) {
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(expected));
    }

    private void awaitEnrollments(Student student, int expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(expected));
    }

    private String chargeKeyOf(Long invoiceId) {
        return jdbc.queryForObject("select charge_key from invoice_line where invoice_id = ?", String.class, invoiceId);
    }

    private Throwable failureOf(Future<?> review) throws InterruptedException {
        try {
            review.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException failure) {
            return failure.getCause();
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new AssertionError("The review did not finish", timeout);
        }
    }

    private long sessionsWaitingOnALock() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Long.class);
    }

    private Throwable[] raceWhileTheAccountIsHeld(Long accountId, Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                accountService.lockStudentCurrentAccount(accountId);
                accountLocked.countDown();
                try {
                    releaseAccount.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(accountLocked.await(30, TimeUnit.SECONDS)).isTrue();
            long waitingBefore = sessionsWaitingOnALock();
            Future<?> firstReview = executor.submit(first);
            await().atMost(Duration.ofSeconds(30)).until(() -> sessionsWaitingOnALock() > waitingBefore);
            Future<?> secondReview = executor.submit(second);
            await().atMost(Duration.ofSeconds(30)).until(() -> sessionsWaitingOnALock() > waitingBefore + 1);
            releaseAccount.countDown();
            holder.get(60, TimeUnit.SECONDS);
            return new Throwable[]{failureOf(firstReview), failureOf(secondReview)};
        } finally {
            releaseAccount.countDown();
            executor.shutdownNow();
        }
    }

    private void assertRequestCancelledAndCanBeMadeAgain(Student student, Course course, CourseEnrollmentApplication first) {
        assertThat(invoiceStatus(first.getInvoiceId())).isEqualTo("CANCELLED");
        assertThat(applicationCancelled(first.getId())).isTrue();
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
        assertThat(invoiceRepository.findLiveInvoiceIdByChargeKey(chargeKeyOf(first.getInvoiceId()))).isEmpty();

        CourseEnrollmentApplication second = request(student, course);

        assertThat(second.getInvoiceId()).isNotEqualTo(first.getInvoiceId());
        assertThat(invoiceStatus(second.getInvoiceId())).isEqualTo("PENDING");
        assertThat(applicationCancelled(second.getId())).isFalse();
        assertThat(applicationCancelled(first.getId())).isTrue();
    }

    @Test
    @Tag("D-258")
    @Tag("B26")
    void approvedForTheFullAmountEnrollsTheStudentAndKeepsTheRequestAlive() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, persistPricedCourse(), "15000.00");

        approveReceipt.approve(requested.receiptId(), persistStaff(), PaymentVerification.none());

        awaitEnrollments(student, 1);
        assertThat(invoiceStatus(requested.invoiceId())).isEqualTo("PAID");
        assertThat(applicationCancelled(requested.application().getId())).isFalse();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-258")
    @Tag("B26")
    void approvedForLessThanTheInvoiceDoesNotEnrollUntilTheRestIsPaid() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, persistPricedCourse(), "15000.00");
        Long invoiceId = requested.invoiceId();
        assertThat(invoiceStatus(invoiceId)).isEqualTo("PAYMENT_UNDER_REVIEW");

        approveReceipt.approve(requested.receiptId(), persistStaff(), new PaymentVerification(ars("10000.00"), null, null));

        assertEnrollmentsStay(student, 0);
        assertThat(invoiceStatus(invoiceId)).isNotEqualTo("PAID").isNotEqualTo("CANCELLED");
        assertThat(applicationCancelled(requested.application().getId())).isFalse();
        BigDecimal paidIntoTheInvoice = jdbc.queryForObject("select paid_amount from invoice where id = ?", BigDecimal.class, invoiceId);
        assertThat(paidIntoTheInvoice.add(creditAvailableIn(invoiceId))).isEqualByComparingTo("10000.00");

        Money outstanding = invoiceRepository.findById(invoiceId).orElseThrow().getAmounts().outstandingAmount().value();
        Person staff = persistStaff();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
        try {
            String fingerprint = previewCashPayment.previewCashPayment(student.getId(), outstanding).fingerprint();
            registerCashPayment.registerCashPayment(student.getId(), new RegisterCashPaymentRequest(outstanding, null, null, null, fingerprint), "b26-rest-" + System.nanoTime());
        } finally {
            SecurityContextHolder.clearContext();
        }

        awaitEnrollments(student, 1);
        assertThat(invoiceStatus(invoiceId)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-258")
    @Tag("B26")
    void approvedForTheFullAmountOfTheRequestOwnReceiptEnrollsTheStudent() {
        Student student = persistStudent();
        CourseEnrollmentApplication application = request(student, persistPricedCourse());

        approveReceipt.approve(application.getPaymentReceipt().getId(), persistStaff(), PaymentVerification.none());

        awaitEnrollments(student, 1);
        assertThat(invoiceStatus(application.getInvoiceId())).isEqualTo("PAID");
        assertThat(applicationCancelled(application.getId())).isFalse();
    }

    @Test
    @Tag("D-242")
    @Tag("B26")
    void aRejectedReceiptOfTheNewFlowCancelsTheInvoiceAndTheRequestAndTheStudentCanAskForTheSameCourseAgain() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        Course course = persistPricedCourse();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, course, "15000.00");

        rejectReceipt.reject(requested.receiptId(), "Comprobante ilegible", persistStaff());

        assertThat(receiptStatus(requested.receiptId())).isEqualTo("REJECTED");
        assertRequestCancelledAndCanBeMadeAgain(student, course, requested.application());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-242")
    @Tag("B26")
    void aRejectedLegacyReceiptOfTheRequestCancelsTheInvoiceAndTheRequestAndTheStudentCanAskForTheSameCourseAgain() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        Course course = persistPricedCourse();
        CourseEnrollmentApplication first = request(student, course);

        rejectReceipt.reject(first.getPaymentReceipt().getId(), "Comprobante ilegible", persistStaff());

        assertThat(receiptStatus(first.getPaymentReceipt().getId())).isEqualTo("REJECTED");
        assertRequestCancelledAndCanBeMadeAgain(student, course, first);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-242")
    @Tag("D-320")
    @Tag("B26")
    void aReceiptRejectedForADifferentTransferDateAlsoCancelsTheInvoiceAndTheRequest() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        Course course = persistPricedCourse();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, course, "15000.00");

        assertThatThrownBy(() -> approveReceipt.approve(requested.receiptId(), persistStaff(), new PaymentVerification(null, UPLOAD_DAY.minusDays(1), null)))
                .isInstanceOf(ReceiptTransferDateMismatchException.class);

        assertThat(receiptStatus(requested.receiptId())).isEqualTo("REJECTED");
        assertRequestCancelledAndCanBeMadeAgain(student, course, requested.application());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-242")
    @Tag("B26")
    void aLateAdmissionPaidEventNeverEnrollsACancelledRequest() {
        Student student = persistStudent();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, persistPricedCourse(), "15000.00");
        rejectReceipt.reject(requested.receiptId(), "Comprobante ilegible", persistStaff());

        transactionTemplate.executeWithoutResult(status -> admissionPaymentListener.confirmCourseEnrollmentOfPaidAdmission(AdmissionInvoicePaidEvent.paidFor(requested.invoiceId())));

        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
        assertThat(applicationCancelled(requested.application().getId())).isTrue();
    }

    @Test
    @Tag("D-242")
    @Tag("D-258")
    @Tag("B26")
    void aRejectionOverlappingAnApprovalCancelsTheRequestAndTheApprovalLosesWithACodedConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, persistPricedCourse(), "15000.00");
        Long receiptId = requested.receiptId();
        Person staff = persistStaff();

        Throwable[] race = raceWhileTheAccountIsHeld(accountOf(requested.invoiceId()),
                () -> rejectReceipt.reject(receiptId, "Rechazo en carrera", staff), () -> approveReceipt.approve(receiptId, staff, PaymentVerification.none()));

        assertThat(race[0]).isNull();
        assertThat(race[1]).isInstanceOf(PaymentReceiptAlreadyReviewedException.class);
        assertThat(receiptStatus(receiptId)).isEqualTo("REJECTED");
        assertThat(invoiceStatus(requested.invoiceId())).isEqualTo("CANCELLED");
        assertThat(applicationCancelled(requested.application().getId())).isTrue();
        assertEnrollmentsStay(student, 0);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-242")
    @Tag("D-258")
    @Tag("B26")
    void anApprovalOverlappingARejectionEnrollsTheStudentAndTheRejectionLosesWithACodedConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Student student = persistStudent();
        NewFlowRequest requested = requestWithTheNewFlowReceipt(student, persistPricedCourse(), "15000.00");
        Long receiptId = requested.receiptId();
        Person staff = persistStaff();

        Throwable[] race = raceWhileTheAccountIsHeld(accountOf(requested.invoiceId()),
                () -> approveReceipt.approve(receiptId, staff, PaymentVerification.none()), () -> rejectReceipt.reject(receiptId, "Rechazo en carrera", staff));

        assertThat(race[0]).isNull();
        assertThat(race[1]).isInstanceOf(PaymentReceiptAlreadyReviewedException.class);
        assertThat(receiptStatus(receiptId)).isEqualTo("APPROVED");
        awaitEnrollments(student, 1);
        assertThat(invoiceStatus(requested.invoiceId())).isEqualTo("PAID");
        assertThat(applicationCancelled(requested.application().getId())).isFalse();
        invariants.assertAllBillingInvariantsHold();
    }
}
