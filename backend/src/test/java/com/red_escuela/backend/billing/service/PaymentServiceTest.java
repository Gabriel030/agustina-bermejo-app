package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTest extends AbstractIntegrationTest {
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
    private NotificationRepository notificationRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private com.red_escuela.backend.billing.invariant.BillingInvariantChecker invariants;

    @org.junit.jupiter.api.BeforeEach
    void startBillingInvariantBaseline() {
        invariants = com.red_escuela.backend.billing.invariant.BillingInvariantChecker.startingNow(jdbc);
    }

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

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, Money amount, InvoicePaymentStatus status) {
        return persistInvoice(enrollment, feeType, amount, status, YearMonth.now());
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, FeeType feeType, Money amount, InvoicePaymentStatus status, YearMonth period) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(feeType).forPeriod(period).dueOn(LocalDate.now()).withBaseAmount(amount).inState(status).build();
        return invoice;
    }

    private Long payInvoiceByBankTransfer(Invoice invoice, Person admin) {
        return BillingTestFixtures.registerApprovedBankTransferPayment(invoice.getId(), admin.getId());
    }

    @Test
    @Tag("D-089")
    @Tag("D-128")
    void aPaymentOfTheNewerInvoiceIsAllocatedToTheOldestOverdueOne() {
        Person admin = persistPerson("admin.oldest." + System.nanoTime());
        Student student = persistStudent("oldest" + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice older = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(1))
                .dueOn(LocalDate.now().minusDays(20)).withBaseAmount(ars(10000)).inState(InvoicePaymentStatus.OVERDUE).build();
        Invoice newer = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        payInvoiceByBankTransfer(newer, admin);

        assertThatInvoice(invoiceRepository.findById(older.getId()).orElseThrow()).isPaid();
        assertThatInvoice(invoiceRepository.findById(newer.getId()).orElseThrow()).isPending();
        assertThat(jdbc.queryForList("select invoice_id from payment_allocation where invoice_id in (?, ?)", Long.class, older.getId(), newer.getId()))
                .containsExactly(older.getId());
        Long accountId = invoiceRepository.findById(older.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, accountId))
                .isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void paymentThatCoversFullBalanceMarksInvoiceAsPaid() {
        Person admin = persistPerson("admin.pago1." + System.nanoTime());
        Student student = persistStudent("96000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);

        payInvoiceByBankTransfer(invoice, admin);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ?", Long.class, invoice.getId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-144")
    @Tag("D-139")
    void aFullPaymentIsRecordedAsAReceivedCreditAndItsAllocationLeavesNoUnallocatedCredit() {
        Person admin = persistPerson("admin.ledger." + System.nanoTime());
        Student student = persistStudent("96009901");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();

        Long paymentId = payInvoiceByBankTransfer(invoice, admin);

        assertThat(jdbc.queryForList("select entry_type, direction, amount, payment_id from current_account_entry where student_current_account_id = ? order by sequence_number", accountId))
                .extracting(row -> row.get("entry_type") + ":" + row.get("direction") + ":" + ((java.math.BigDecimal) row.get("amount")).setScale(0) + ":" + row.get("payment_id"))
                .containsExactly("INVOICE_ISSUED:DEBIT:10000:null", "PAYMENT_RECEIVED:CREDIT:10000:" + paymentId);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, accountId))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select balance_amount from student_current_account where id = ?", java.math.BigDecimal.class, accountId))
                .isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-091")
    @Tag("D-127")
    void payingACancelledInvoiceIsRejectedAndNothingIsRecorded() {
        Person admin = persistPerson("admin.cancelled." + System.nanoTime());
        Student student = persistStudent("96009902");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING);
        jdbc.update("update invoice set payment_status = 'CANCELLED', paid_amount = 0, outstanding_amount = 0, credited_amount = total where id = ?", invoice.getId());
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();

        assertThatThrownBy(() -> payInvoiceByBankTransfer(invoice, admin)).isInstanceOf(InvoiceNotPayableException.class);

        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ?", Long.class, invoice.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class, accountId))
                .isZero();
    }

    private CourseEnrollment persistCourseEnrollmentWithStanding(Student student, EnrollmentPaymentStatus standing) {
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        enrollment.setPaymentStatus(standing);
        enrollment.setPaymentStatusUpdatedAt(LocalDateTime.now().minusMonths(1));
        return courseEnrollmentRepository.save(enrollment);
    }

    @Test
    void payingTheOnlyOverdueInstallmentRestoresTheEnrollmentToUpToDate() {
        Person admin = persistPerson("admin.pago.standing1." + System.nanoTime());
        Student student = persistStudent("96000101");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.OVERDUE);
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE);
        persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.PENDING, YearMonth.now().plusMonths(1));

        payInvoiceByBankTransfer(overdue, admin);

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

        payInvoiceByBankTransfer(first, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.OVERDUE);
    }

    @Test
    void settlingAllOverdueDebtOfASuspendedEnrollmentLiftsTheSuspensionAndNotifiesTheStudent() {
        Person admin = persistPerson("admin.pago.standing3." + System.nanoTime());
        Student student = persistStudent("96000103");
        CourseEnrollment enrollment = persistCourseEnrollmentWithStanding(student, EnrollmentPaymentStatus.SUSPENDED);
        Invoice overdue = persistInvoice(enrollment, FeeType.MONTHLY_INSTALLMENT, ars(10000), InvoicePaymentStatus.OVERDUE);

        payInvoiceByBankTransfer(overdue, admin);

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

        payInvoiceByBankTransfer(overdue, admin);

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
    }
}
