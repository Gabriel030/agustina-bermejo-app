package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CreditNoteRepository creditNoteRepository;

    @Autowired
    private CreditNoteService creditNoteService;

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
    private NotificationRepository notificationRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
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

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, Money amount, InvoicePaymentStatus status) {
        return persistInvoice(enrollment, feeType, amount, status, YearMonth.now());
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, Money amount, InvoicePaymentStatus status, YearMonth period) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(feeType).forPeriod(period).dueOn(LocalDate.now()).withBaseAmount(amount).inState(status).build();
        return invoiceRepository.save(invoice);
    }

    @Test
    void paymentThatCoversFullBalanceMarksInvoiceAsPaid() {
        Person admin = persistPerson("admin.pago1." + System.nanoTime());
        Student student = persistStudent("96000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(10000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void payingACancelledInvoiceIsRejected() {
        Person admin = persistPerson("admin.pago2." + System.nanoTime());
        Student student = persistStudent("96000002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.CANCELLED);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(10000))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectedPaymentDoesNotAffectBalanceButIsRecorded() {
        Person admin = persistPerson("admin.pago3." + System.nanoTime());
        Student student = persistStudent("96000003");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(10000))),
                PaymentMethod.CARD, true, "Tarjeta rechazada", null, null, null), admin);

        assertThat(payment.getStatus().name()).isEqualTo("REJECTED");
        assertThat(payment.getAllocations()).isEmpty();
        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isPending();
    }

    @Test
    @Tag("D-084")
    void payingEnrollmentFeePlusFiveInstallmentsInOnePaymentLeavesAllSixInvoicesPaid() {
        Person admin = persistPerson("admin.pago4." + System.nanoTime());
        Student student = persistStudent("96000004");
        CourseEnrollment enrollment = persistCourseEnrollment(student);

        YearMonth base = YearMonth.now();
        Invoice enrollmentFee = persistInvoice(enrollment, FeeType.ENROLLMENT_FEE, ars(5000), InvoicePaymentStatus.PENDING, base);
        List<Invoice> installments = List.of(
                persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING, base),
                persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING, base.plusMonths(1)),
                persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING, base.plusMonths(2)),
                persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING, base.plusMonths(3)),
                persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING, base.plusMonths(4)));

        List<PaymentAllocationRequest> allocations = new java.util.ArrayList<>();
        allocations.add(new PaymentAllocationRequest(enrollmentFee.getId(), ars(5000)));
        installments.forEach(invoice -> allocations.add(new PaymentAllocationRequest(invoice.getId(), ars(1000))));

        Payment payment = registerPaymentUseCase.register(
                new RegisterPaymentRequest(allocations, PaymentMethod.TRANSFER, false, null, null, null, null), admin);

        assertThat(payment.getAllocations()).hasSize(6);
        assertThatInvoice(invoiceRepository.findById(enrollmentFee.getId()).orElseThrow()).isPaid();
        installments.forEach(invoice ->
                assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid());
    }

    @Test
    void amountAboveRemainingBalanceIsRejected() {
        Person admin = persistPerson("admin.pago5." + System.nanoTime());
        Student student = persistStudent("96000005");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(15000))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(PaymentExceedsInvoiceBalanceException.class);
    }

    @Test
    void negativeAmountAppliedIsRejected() {
        Person admin = persistPerson("admin.pago9." + System.nanoTime());
        Student student = persistStudent("96000009");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(-100))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroAmountAppliedIsRejected() {
        Person admin = persistPerson("admin.pago10." + System.nanoTime());
        Student student = persistStudent("96000010");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(0))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStudentCanPayTheirOwnInvoice() {
        Student student = persistStudent("96000006");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        authenticateAs(student.getPerson());
        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(10000))),
                PaymentMethod.CASH, false, null, null, null, null), student.getPerson());

        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void aStudentCannotPaySomeoneElsesInvoice() {
        Student owner = persistStudent("96000007");
        Student intruder = persistStudent("96000008");
        CourseEnrollment enrollment = persistCourseEnrollment(owner);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        authenticateAs(intruder.getPerson());
        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(10000))),
                PaymentMethod.CASH, false, null, null, null, null), intruder.getPerson()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void creditBalancePaymentConsumesTheCreditNoteAndStillPaysTheInvoice() {
        Person admin = persistPerson("admin.pago11." + System.nanoTime());
        Student student = persistStudent("96000011");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING);
        creditNoteService.createFor(student, ars(1500), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Ajuste", admin);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(1000))),
                PaymentMethod.CREDIT_BALANCE, false, null, null, null, null), admin);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        CreditNote reloaded = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).get(0);
        assertThat(reloaded.getStatus().name()).isEqualTo("PARTIALLY_CONSUMED");
        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void creditBalancePaymentWithoutEnoughCreditIsRejectedAndNothingChanges() {
        Person admin = persistPerson("admin.pago12." + System.nanoTime());
        Student student = persistStudent("96000012");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(1000), InvoicePaymentStatus.PENDING);
        creditNoteService.createFor(student, ars(300), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Ajuste", admin);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(1000))),
                PaymentMethod.CREDIT_BALANCE, false, null, null, null, null), admin))
                .isInstanceOf(InsufficientCreditBalanceException.class);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();
    }

    private CourseEnrollment persistCourseEnrollmentWithStanding(Student student, EnrollmentPaymentStatus standing) {
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        enrollment.setPaymentStatus(standing);
        enrollment.setPaymentStatusUpdatedAt(LocalDateTime.now().minusMonths(1));
        return courseEnrollmentRepository.save(enrollment);
    }

    private void payInFull(Invoice invoice, Person admin) {
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), invoice.getAmounts().linesTotal().value())),
                PaymentMethod.CASH, false, null, null, null, null), admin);
    }

    @Test
    void payingTheOnlyOverdueInstallmentRestoresTheEnrollmentToUpToDate() {
        Person admin = persistPerson("admin.pago.standing1." + System.nanoTime());
        Student student = persistStudent("96000101");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.OVERDUE);
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING, YearMonth.now().plusMonths(1));

        payInFull(overdue, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    void payingOnlyPartOfTheOverdueDebtKeepsTheEnrollmentOverdue() {
        Person admin = persistPerson("admin.pago.standing2." + System.nanoTime());
        Student student = persistStudent("96000102");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.OVERDUE);
        Invoice first = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE, YearMonth.now().minusMonths(2));
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE, YearMonth.now().minusMonths(1));

        payInFull(first, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.OVERDUE);
    }

    @Test
    void settlingAllOverdueDebtOfASuspendedEnrollmentLiftsTheSuspensionAndNotifiesTheStudent() {
        Person admin = persistPerson("admin.pago.standing3." + System.nanoTime());
        Student student = persistStudent("96000103");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.SUSPENDED);
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE);

        payInFull(overdue, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(student.getPerson().getId()))
                .extracting(Notification::getType)
                .contains(NotificationType.SUSPENSION_LIFTED);
    }

    @Test
    void payingDoesNotRestoreAnEnrollmentWithdrawnForNonPayment() {
        Person admin = persistPerson("admin.pago.standing4." + System.nanoTime());
        Student student = persistStudent("96000104");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE);

        payInFull(overdue, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
    }
}
