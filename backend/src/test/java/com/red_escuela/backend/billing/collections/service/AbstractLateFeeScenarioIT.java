package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractLateFeeScenarioIT extends AbstractIntegrationTest {
    protected static final StatusChangeActor ACTOR = new StatusChangeActor(null, "LateFeeScenarioIT");
    private static final AtomicLong EXTERNAL_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L * 1000);

    @Autowired
    protected LateFeeAccrualService accrualService;
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected StudentCurrentAccountService accountService;
    @Autowired
    protected CurrentAccountAllocationService allocationService;
    @Autowired
    protected PaymentAllocationRepository allocationRepository;
    @Autowired
    protected TransactionTemplate transactionTemplate;
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

    protected BillingInvariantChecker invariants;
    private java.sql.Timestamp scenarioStart;
    protected Person person;
    protected Student student;
    protected Term term;
    protected CourseEnrollment enrollment;

    protected void arrange(int year) {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        scenarioStart = jdbc.queryForObject("select now()", java.sql.Timestamp.class);
        String username = "late.fee.settle." + year + "." + System.nanoTime();
        Address address = addressRepository.save(new Address());
        Person created = new Person();
        created.setUsername(username);
        created.setDni(Long.toString(Math.abs((long) username.hashCode())));
        created.setName("Persona");
        created.setLastname("Recargo");
        created.setEmail(username + "@example.com");
        created.setAddress(address);
        created.setStatus(StatusType.ACTIVE);
        person = personRepository.save(created);
        Institution institution = new Institution();
        institution.setName("Instituto recargo " + System.nanoTime());
        institution = institutionRepository.save(institution);
        student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);
        term = new Term();
        term.setYear(year);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        Course course = new Course();
        course.setName("Curso recargo " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment = courseEnrollmentRepository.save(courseEnrollment);
    }

    protected void publish(int graceDays, String rate, int ndDays, boolean freeze, boolean enabled, LocalDate effectiveFrom) {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, person, graceDays, new BigDecimal(rate), 30, 1, ndDays, freeze, enabled, effectiveFrom);
    }

    protected void publishDefaults(int year) {
        publish(0, "0.002", 10, true, true, LocalDate.of(year, 1, 1));
    }

    protected Invoice overdueInstallmentDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).overdue().build();
    }

    protected Invoice pendingInstallmentDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).build();
    }

    protected void withdrawCourseEnrollmentOn(LocalDate date) {
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setCompletionDate(date.atTime(10, 0));
        courseEnrollmentRepository.save(enrollment);
    }

    protected CurrentAccountEntry receiveMoney(Invoice origin, String amount, LocalDate paidOn) {
        return transactionTemplate.execute(status -> accountService.recordPaymentReceivedCredit(
                accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()), EXTERNAL_IDS.incrementAndGet(), NonNegativeMoney.of(ars(amount)), paidOn, "Pago"));
    }

    protected void allocateOn(Invoice origin, LocalDate businessDate) {
        transactionTemplate.executeWithoutResult(status -> allocationService.allocateAccountCreditToOldestInvoices(
                accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()), businessDate, ACTOR));
    }

    protected CurrentAccountEntry payInFull(Invoice origin, LocalDate paymentDate, LocalDate allocationDate) {
        CurrentAccountEntry credit = receiveMoney(origin, "100000.00", paymentDate);
        allocateOn(origin, allocationDate);
        return credit;
    }

    protected void reversePayment(Invoice origin, CurrentAccountEntry credit, LocalDate businessDate, int graceDays) {
        PaymentAllocation current = allocationRepository.findCurrentAllocationByInvoiceId(origin.getId()).orElseThrow();
        transactionTemplate.executeWithoutResult(status -> {
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId());
            allocationService.reverseAllocation(account, current, businessDate, graceDays, "Contracargo", ACTOR);
            accountService.recordPaymentReversalDebit(account, credit, businessDate, "Contracargo");
        });
    }

    protected void awaitEventsDrained() {
        await().atMost(Duration.ofSeconds(25)).until(() -> jdbc.queryForObject("select count(*) from event_publication where completion_date is null and publication_date >= ?", Long.class, scenarioStart) == 0L);
    }

    protected List<Map<String, Object>> chargesOf(Invoice origin) {
        return jdbc.queryForList("select c.*, d.total as nd_total, d.due_date as nd_due, d.issue_date as nd_issue from late_fee_charge c join invoice d on d.id = c.debit_note_id "
                + "where c.origin_invoice_id = ? order by c.period_start", origin.getId());
    }

    protected List<String> summaryOf(Invoice origin) {
        return chargesOf(origin).stream().map(c -> c.get("period_start") + ".." + c.get("period_end") + ":" + c.get("charged_days") + ":" + c.get("nd_total")).toList();
    }

    protected Invoice reload(Invoice invoice) {
        return invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
    }

    protected void reconcile(Invoice origin, LocalDate date) {
        accrualService.reconcileLateFeesOfInvoice(origin.getId(), date);
    }

    protected static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    protected void assertScenarioInvariants(LocalDate today) {
        invariants.assertAllBillingInvariantsHold();
        invariants.assertLateFeesAreCompleteAt(today);
    }

    protected Money price() {
        return ars(100000);
    }
}
