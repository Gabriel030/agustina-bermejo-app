package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.operation.PaymentVerification;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class PaymentReceiptServiceTest extends AbstractIntegrationTest {
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.ListPendingPaymentReceiptsUseCase listPendingPaymentReceiptsUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.FindOwnPaymentReceiptsUseCase findOwnPaymentReceiptsUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private com.red_escuela.backend.billing.repository.BankTransferReceiptRepository bankTransferReceiptRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private CourseEnrollment persistCourseEnrollment(Student student) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, InvoicePaymentStatus status) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now()).dueOn(LocalDate.now()).withBaseAmount(amount).inState(status).build();
        return invoice;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student) {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(ProgramEnrollment enrollment, Money amount, InvoicePaymentStatus status) {
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now()).dueOn(LocalDate.now()).withBaseAmount(amount).inState(status).build();
        return invoice;
    }

    private MockMultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void uploadingAReceiptLeavesItPendingReviewWithoutTouchingTheInvoice() {
        Person admin = persistPerson("admin.receipt1." + System.nanoTime());
        Student student = persistStudent("97000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);

        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        assertThat(receipt.getStatus()).isEqualTo(BankTransferReceiptStatus.PENDING_REVIEW);
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "Limpieza de test", admin);
    }

    @Test
    void cannotUploadASecondReceiptWhileTheFirstIsStillPending() {
        Person admin = persistPerson("admin.receipt2." + System.nanoTime());
        Student student = persistStudent("97000002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        assertThatThrownBy(() -> uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile()))
                .isInstanceOf(PaymentReceiptAlreadyPendingException.class);

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "Limpieza de test", admin);
    }

    @Test
    void approvingCreatesARealPaymentAndMarksTheInvoiceAsPaid() {
        Person admin = persistPerson("admin.receipt3." + System.nanoTime());
        Student student = persistStudent("97000003");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        BankTransferReceipt approved = approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        assertThat(approved.getStatus()).isEqualTo(BankTransferReceiptStatus.APPROVED);
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
    }

    @Test
    @Tag("D-376")
    void approvingARealReceiptCreatesAnApprovedTransferPaymentLinkedToTheReceiptWithItsAllocationSource() {
        Person admin = persistPerson("admin.d376." + System.nanoTime());
        Student student = persistStudent("97376001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        BankTransferReceipt approved = approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        Long paymentId = approved.getPaymentId();
        assertThat(paymentId).isNotNull();
        java.util.Map<String, Object> payment = jdbcTemplate.queryForMap("select status, gateway, method_type, amount, approved_amount, reviewed_by_person_id, registered_by_person_id from payment where id = ?", paymentId);
        assertThat(payment).containsEntry("status", "APPROVED").containsEntry("gateway", "MANUAL").containsEntry("method_type", "BANK_TRANSFER")
                .containsEntry("reviewed_by_person_id", admin.getId()).containsEntry("registered_by_person_id", admin.getId());
        assertThat((java.math.BigDecimal) payment.get("amount")).isEqualByComparingTo("10000");
        assertThat((java.math.BigDecimal) payment.get("approved_amount")).isEqualByComparingTo("10000");
        assertThat(jdbcTemplate.queryForList("select s.payment_id, s.amount from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ?", invoice.getId()))
                .singleElement().satisfies(source -> {
                    assertThat(source.get("payment_id")).isEqualTo(paymentId);
                    assertThat((java.math.BigDecimal) source.get("amount")).isEqualByComparingTo("10000");
                });
    }

    @Test
    @Tag("D-376")
    void approvingTheSameReceiptTwiceSequentiallyCreditsTheAccountOnlyOnce() {
        com.red_escuela.backend.billing.invariant.BillingInvariantChecker invariants = com.red_escuela.backend.billing.invariant.BillingInvariantChecker.startingNow(jdbcTemplate);
        Person admin = persistPerson("admin.idem1." + System.nanoTime());
        Student student = persistStudent("97376002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        assertThatThrownBy(() -> approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none())).isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class);

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class, accountId)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-376")
    void twoAdminsApprovingTheSameReceiptAtTheSameTimeCreditTheAccountOnlyOnce() throws Exception {
        com.red_escuela.backend.billing.invariant.BillingInvariantChecker invariants = com.red_escuela.backend.billing.invariant.BillingInvariantChecker.startingNow(jdbcTemplate);
        Person admin1 = persistPerson("admin.idem2a." + System.nanoTime());
        Person admin2 = persistPerson("admin.idem2b." + System.nanoTime());
        Student student = persistStudent("97376003");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<BankTransferReceipt>> attempts = List.of(
                executor.submit(() -> {
                    start.await();
                    return approvePaymentReceiptUseCase.approve(receipt.getId(), admin1, PaymentVerification.none());
                }),
                executor.submit(() -> {
                    start.await();
                    return approvePaymentReceiptUseCase.approve(receipt.getId(), admin2, PaymentVerification.none());
                }));
        start.countDown();
        int approvals = 0;
        for (Future<BankTransferReceipt> attempt : attempts) {
            try {
                attempt.get(60, TimeUnit.SECONDS);
                approvals++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class,
                        com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class, InvoiceNotPayableException.class);
            }
        }
        executor.shutdownNow();

        assertThat(approvals).isEqualTo(1);
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbcTemplate.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId)).isEqualTo(1L);
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void rejectingWithAReasonAllowsTheStudentToRetry() {
        Person admin = persistPerson("admin.receipt4." + System.nanoTime());
        Student student = persistStudent("97000004");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        BankTransferReceipt rejected = rejectPaymentReceiptUseCase.reject(receipt.getId(), "Comprobante ilegible", admin);
        assertThat(rejected.getStatus()).isEqualTo(BankTransferReceiptStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Comprobante ilegible");

        BankTransferReceipt retry = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        assertThat(retry.getStatus()).isEqualTo(BankTransferReceiptStatus.PENDING_REVIEW);

        rejectPaymentReceiptUseCase.reject(retry.getId(), "Limpieza de test", admin);
    }

    @Test
    void aStudentCannotUploadAReceiptForSomeoneElsesInvoice() {
        Student owner = persistStudent("97000005");
        Student intruder = persistStudent("97000006");
        CourseEnrollment enrollment = persistCourseEnrollment(owner);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> uploadPaymentReceiptUseCase.upload(invoice.getId(), intruder.getId(), receiptFile()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void approvingAndRejectingInParallelOnTheSameReceiptOnlyOneWins() throws Exception {
        Person admin1 = persistPerson("admin.receipt8a." + System.nanoTime());
        Person admin2 = persistPerson("admin.receipt8b." + System.nanoTime());
        Student student = persistStudent("97000008");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<BankTransferReceipt> intentoAprobar = () -> {
            listos.countDown();
            arranca.await();
            return approvePaymentReceiptUseCase.approve(receipt.getId(), admin1, PaymentVerification.none());
        };
        Callable<BankTransferReceipt> intentoRechazar = () -> {
            listos.countDown();
            arranca.await();
            return rejectPaymentReceiptUseCase.reject(receipt.getId(), "Comprobante ilegible", admin2);
        };

        Future<BankTransferReceipt> resultadoAprobar = executor.submit(intentoAprobar);
        Future<BankTransferReceipt> resultadoRechazar = executor.submit(intentoRechazar);
        listos.await(5, TimeUnit.SECONDS);
        arranca.countDown();

        int exitos = 0;
        int conflictos = 0;
        for (Future<BankTransferReceipt> resultado : List.of(resultadoAprobar, resultadoRechazar)) {
            try {
                resultado.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(
                        com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class,
                        com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class);
                conflictos++;
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(conflictos).isEqualTo(1);

        BankTransferReceipt reloaded = bankTransferReceiptRepository.findById(receipt.getId()).orElseThrow();
        Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        if (reloaded.getStatus() == BankTransferReceiptStatus.APPROVED) {
            assertThatInvoice(reloadedInvoice).isPaid();
        } else {
            assertThat(reloaded.getStatus()).isEqualTo(BankTransferReceiptStatus.REJECTED);
            assertThatInvoice(reloadedInvoice).isPending();
        }
    }

    @Test
    void uploadingAReceiptOnAnAlreadyPaidInvoiceIsRejected() {
        Student student = persistStudent("97000007");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PAID);

        assertThatThrownBy(() -> uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile()))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void findOwnIncludesReceiptsFromProgramEnrollmentInvoicesNotOnlyCourseEnrollment() {
        Person admin = persistPerson("admin.receipt9." + System.nanoTime());
        Student student = persistStudent("97000009");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student);
        Invoice invoice = persistInvoice(programEnrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        List<BankTransferReceipt> ownReceipts = findOwnPaymentReceiptsUseCase.findOwn(student.getId());

        assertThat(ownReceipts).extracting(BankTransferReceipt::getId).contains(receipt.getId());

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "Limpieza de test", admin);
    }

    private List<Notification> notificationsOf(Student student) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(student.getPerson().getId());
    }

    @Test
    void rejectingAReceiptNotifiesTheStudentWithTheReason() {
        Person admin = persistPerson("admin.receipt10." + System.nanoTime());
        Student student = persistStudent("97000010");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "El importe no coincide con la cuota", admin);

        assertThat(notificationsOf(student))
                .filteredOn(notification -> notification.getType() == NotificationType.PAYMENT_RECEIPT_REJECTED)
                .singleElement()
                .satisfies(notification -> assertThat(notification.getMessage()).contains("El importe no coincide con la cuota"));
    }

    @Test
    void approvingAReceiptNotifiesTheStudentThatThePaymentWasCredited() {
        Person admin = persistPerson("admin.receipt11." + System.nanoTime());
        Student student = persistStudent("97000011");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        assertThat(notificationsOf(student))
                .extracting(Notification::getType)
                .contains(NotificationType.PAYMENT_RECEIPT_APPROVED);
    }

    @Test
    @Tag("D-235")
    @Tag("D-089")
    void approvingAReceiptOfANewerInvoiceWhileAnOlderOneIsOverdueSettlesTheOlderOneAndKeepsTheReceiptInTheAppliedToOlderDebtState() {
        Person admin = persistPerson("admin.d235a." + System.nanoTime());
        Student student = persistStudent("97235001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice older = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(1))
                .dueOn(LocalDate.now().minusDays(20)).withBaseAmount(ars(10000)).inState(InvoicePaymentStatus.OVERDUE).build();
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        BankTransferReceipt approved = approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        assertThat(approved.getStatus()).isEqualTo(BankTransferReceiptStatus.APPLIED_TO_DEBT);
        assertThatInvoice(invoiceRepository.findById(older.getId()).orElseThrow()).isPaid();
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationsOf(student)).extracting(Notification::getType).contains(NotificationType.PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT));
        assertThat(notificationsOf(student)).extracting(Notification::getType).doesNotContain(NotificationType.PAYMENT_RECEIPT_APPROVED);
        BankTransferReceipt second = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        assertThat(second.getStatus()).isEqualTo(BankTransferReceiptStatus.PENDING_REVIEW);
        rejectPaymentReceiptUseCase.reject(second.getId(), "Limpieza de test", admin);
    }

    @Test
    @Tag("D-235")
    void approvingAReceiptWithoutOlderDebtSettlesItsInvoiceAndNotifiesTheNormalApproval() {
        Person admin = persistPerson("admin.d235b." + System.nanoTime());
        Student student = persistStudent("97235002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());

        BankTransferReceipt approved = approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none());

        assertThat(approved.getStatus()).isEqualTo(BankTransferReceiptStatus.APPROVED);
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(notificationsOf(student)).extracting(Notification::getType)
                .contains(NotificationType.PAYMENT_RECEIPT_APPROVED)
                .doesNotContain(NotificationType.PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT);
    }

    @Test
    void approvingAReceiptWhoseInstallmentWasAlreadyPaidAtTheCounterFailsAsNotPayable() {
        Person admin = persistPerson("admin.receipt12." + System.nanoTime());
        Student student = persistStudent("97000012");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.PENDING);
        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        BillingTestFixtures.payInvoiceInFull(invoice);

        assertThatThrownBy(() -> approvePaymentReceiptUseCase.approve(receipt.getId(), admin, PaymentVerification.none()))
                .isInstanceOf(InvoiceNotPayableException.class);
        assertThat(bankTransferReceiptRepository.findById(receipt.getId()).orElseThrow().getStatus())
                .isEqualTo(BankTransferReceiptStatus.PENDING_REVIEW);

        rejectPaymentReceiptUseCase.reject(receipt.getId(), "Limpieza de test", admin);
    }

    @Test
    void receiptOfAnInvoiceWithoutEnrollmentShowsTheUploaderNameAndDni() {
        Student student = persistStudent("97000013");
        student.getPerson().setName("Ana");
        student.getPerson().setLastname("Comprobante");
        student.getPerson().setDni("97000013");
        personRepository.save(student.getPerson());
        Invoice invoice = BillingTestFixtures.invoice().forStudent(student).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.now()).dueOn(LocalDate.now()).withBaseAmount(ars(10000)).build();
        uploadPaymentReceiptUseCase.upload(invoice.getId(), student.getId(), receiptFile());
        Long invoiceId = invoice.getId();

        BankTransferReceiptResponse response = new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status ->
                BankTransferReceiptResponse.from(bankTransferReceiptRepository.findByInvoiceIdsWithPeople(List.of(invoiceId)).get(0), student.getPerson(), null, List.of(invoiceId)));

        assertThat(response.studentName()).isEqualTo("Ana Comprobante");
        assertThat(response.studentDni()).isEqualTo("97000013");
    }
}
