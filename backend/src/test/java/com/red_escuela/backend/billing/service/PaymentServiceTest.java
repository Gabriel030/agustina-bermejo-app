package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.2 (Pagos y allocations): tests 10, 13, 14, 19 de la spec. NO @Transactional a
 * propósito — mismo motivo que ProgramCohortBillingTest (findByIdForUpdate necesita su propia
 * transacción real, no una anidada bajo un test @Transactional).
 */
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

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, BigDecimal amount, InvoiceStatus status) {
        return persistInvoice(enrollment, feeType, amount, status, YearMonth.now());
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, BigDecimal amount, InvoiceStatus status, YearMonth period) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    @Test
    void paymentThatCoversFullBalanceMarksInvoiceAsPaid() {
        Person admin = persistPerson("admin.pago1." + System.nanoTime());
        Student student = persistStudent("96000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(10000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        // getRemainingBalance() necesita la colección lazy de allocations, que no se puede
        // inicializar fuera de una sesión activa (test sin @Transactional a propósito, ver
        // javadoc de la clase) — el status PAID ya prueba lo que importa acá.
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void payingACancelledInvoiceIsRejected() {
        Person admin = persistPerson("admin.pago2." + System.nanoTime());
        Student student = persistStudent("96000002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.CANCELLED);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(10000))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectedPaymentDoesNotAffectBalanceButIsRecorded() {
        Person admin = persistPerson("admin.pago3." + System.nanoTime());
        Student student = persistStudent("96000003");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(10000))),
                PaymentMethod.CARD, true, "Tarjeta rechazada", null, null, null), admin);

        assertThat(payment.getStatus().name()).isEqualTo("REJECTED");
        assertThat(payment.getAllocations()).isEmpty();
        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void payingEnrollmentFeePlusFiveInstallmentsInOnePaymentLeavesAllSixInvoicesPaid() {
        Person admin = persistPerson("admin.pago4." + System.nanoTime());
        Student student = persistStudent("96000004");
        CourseEnrollment enrollment = persistCourseEnrollment(student);

        YearMonth base = YearMonth.now();
        Invoice enrollmentFee = persistInvoice(enrollment, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(5000), InvoiceStatus.PENDING, base);
        List<Invoice> installments = List.of(
                persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, base),
                persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, base.plusMonths(1)),
                persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, base.plusMonths(2)),
                persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, base.plusMonths(3)),
                persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, base.plusMonths(4)));

        List<PaymentAllocationRequest> allocations = new java.util.ArrayList<>();
        allocations.add(new PaymentAllocationRequest(enrollmentFee.getId(), BigDecimal.valueOf(5000)));
        installments.forEach(invoice -> allocations.add(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(1000))));

        Payment payment = registerPaymentUseCase.register(
                new RegisterPaymentRequest(allocations, PaymentMethod.TRANSFER, false, null, null, null, null), admin);

        assertThat(payment.getAllocations()).hasSize(6);
        assertThat(invoiceRepository.findById(enrollmentFee.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        installments.forEach(invoice ->
                assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID));
    }

    @Test
    void amountAboveRemainingBalanceIsRejected() {
        Person admin = persistPerson("admin.pago5." + System.nanoTime());
        Student student = persistStudent("96000005");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(15000))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(PaymentExceedsInvoiceBalanceException.class);
    }

    // Regresión (auditoría 2026-09-04): un amountApplied negativo nunca superaba
    // getRemainingBalance() (esa comparación siempre es falsa para negativos), así que pasaba el
    // chequeo sin disparar nunca PAID — corrompía el saldo en silencio.
    @Test
    void negativeAmountAppliedIsRejected() {
        Person admin = persistPerson("admin.pago9." + System.nanoTime());
        Student student = persistStudent("96000009");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(-100))),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroAmountAppliedIsRejected() {
        Person admin = persistPerson("admin.pago10." + System.nanoTime());
        Student student = persistStudent("96000010");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.ZERO)),
                PaymentMethod.CASH, false, null, null, null, null), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStudentCanPayTheirOwnInvoice() {
        Student student = persistStudent("96000006");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        authenticateAs(student.getPerson());
        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(10000))),
                PaymentMethod.CASH, false, null, null, null, null), student.getPerson());

        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void aStudentCannotPaySomeoneElsesInvoice() {
        Student owner = persistStudent("96000007");
        Student intruder = persistStudent("96000008");
        CourseEnrollment enrollment = persistCourseEnrollment(owner);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), InvoiceStatus.PENDING);

        authenticateAs(intruder.getPerson());
        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(10000))),
                PaymentMethod.CASH, false, null, null, null, null), intruder.getPerson()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void creditBalancePaymentConsumesTheCreditNoteAndStillPaysTheInvoice() {
        Person admin = persistPerson("admin.pago11." + System.nanoTime());
        Student student = persistStudent("96000011");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING);
        creditNoteService.createFor(student, BigDecimal.valueOf(1500), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Ajuste", admin);

        Payment payment = registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(1000))),
                PaymentMethod.CREDIT_BALANCE, false, null, null, null, null), admin);

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        // getRemainingAmount() necesita la colección lazy de applications, que no se puede
        // inicializar fuera de una sesión activa (test sin @Transactional a propósito, mismo
        // motivo que paymentThatCoversFullBalanceMarksInvoiceAsPaid más arriba) — el status
        // PARTIALLY_CONSUMED ya prueba que se consumió parcialmente.
        CreditNote reloaded = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).get(0);
        assertThat(reloaded.getStatus().name()).isEqualTo("PARTIALLY_CONSUMED");
        assertThat(payment.getAllocations()).hasSize(1);
    }

    @Test
    void creditBalancePaymentWithoutEnoughCreditIsRejectedAndNothingChanges() {
        Person admin = persistPerson("admin.pago12." + System.nanoTime());
        Student student = persistStudent("96000012");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.RECURRING_FEE, BigDecimal.valueOf(1000), InvoiceStatus.PENDING);
        creditNoteService.createFor(student, BigDecimal.valueOf(300), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Ajuste", admin);

        assertThatThrownBy(() -> registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), BigDecimal.valueOf(1000))),
                PaymentMethod.CREDIT_BALANCE, false, null, null, null, null), admin))
                .isInstanceOf(InsufficientCreditBalanceException.class);

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }
}
