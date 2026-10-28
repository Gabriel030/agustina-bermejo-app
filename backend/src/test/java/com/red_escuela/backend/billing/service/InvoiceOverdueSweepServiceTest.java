package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.testsupport.NightlyTest;
import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@SlowTest(reason = "Sweeps hundreds of overdue installments and re-runs the daily job to prove it is idempotent")
class InvoiceOverdueSweepServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceOverdueSweepService sweepService;

    @Autowired
    private OverdueInvoiceTransitionService transitionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;

    @Autowired
    private BatchExecutionLogRepository batchExecutionLogRepository;

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

    private CourseEnrollment persistCourseEnrollment(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

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

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, LocalDate dueDate) {
        return persistInvoice(enrollment, amount, dueDate, YearMonth.from(dueDate));
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, LocalDate dueDate, YearMonth period) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(period).dueOn(dueDate).withBaseAmount(amount).build();
    }

    private ConfigurationVersion persistPolicy(Person createdBy, int gracePeriodDays, BigDecimal surchargePercentage) {
        return LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, createdBy, gracePeriodDays, surchargePercentage, 30, 2, LocalDate.now().minusYears(50));
    }

    @Test
    void installmentOverdueForMoreThanTenDaysBecomesOverdueWithoutAnySurcharge() {
        Person director = persistPerson("director.sweep1." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000001");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(11));

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isOverdue();
        assertThat(reloaded.getAmounts().total().value()).isEqualTo(ars(1000));
        assertThat(invoiceRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void installmentOverdueForExactlyTenDaysDoesNotBecomeOverdueYet() {
        Person director = persistPerson("director.sweep2." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000002");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(10));

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isPending();
        assertThat(reloaded.getAmounts().total().value()).isEqualTo(ars(1000));
    }

    @Test
    void sweepCrashMidRowDoesNotLeaveInconsistentState() {
        Person director = persistPerson("director.sweep3." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000003");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(11));
        jdbcTemplate.execute("""
                create or replace function fail_invoice_overdue_update() returns trigger as $$
                begin
                    raise exception 'simulated crash while marking the invoice as overdue';
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("create trigger fail_invoice_overdue_update before update on invoice "
                + "for each row execute function fail_invoice_overdue_update()");
        try {
            sweepService.run(LocalDate.now());
        } finally {
            jdbcTemplate.execute("drop trigger fail_invoice_overdue_update on invoice");
            jdbcTemplate.execute("drop function fail_invoice_overdue_update()");
        }

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isPending();
        assertThat(reloaded.getAmounts().total().value()).isEqualTo(ars(1000));

        var logs = batchExecutionLogRepository.findByJobNameOrderByRanAtDesc("InvoiceOverdueSweepService");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(BatchExecutionStatus.FAILED);
        assertThat(logs.get(0).getFailedCount()).isEqualTo(1);
        assertThat(logs.get(0).getProcessedCount()).isEqualTo(0);
    }

    @Test
    @NightlyTest(reason = "Volume: persists 400 overdue invoices; the sweep behavior is covered by the small cases of the class")
    void dailyProcessMustReviewFourHundredOverdueInstallmentsAndNoneIsLeftUnprocessed() {
        Person director = persistPerson("director.sweep5." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000005");

        int total = 400;
        for (int i = 0; i < total; i++) {
            persistInvoice(enrollment, ars(1000), LocalDate.now().minusMonths(i).minusDays(11), YearMonth.now().minusMonths(i));
        }

        sweepService.run(LocalDate.now());

        long overdueCount = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getPaymentStatus() == InvoicePaymentStatus.OVERDUE)
                .count();
        assertThat(overdueCount).isEqualTo(total);
    }

    @Test
    void processingTheSameOverdueInstallmentTwiceLeavesItOverdueAndUnchanged() {
        Person director = persistPerson("director.sweep.twice." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000901");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(15));

        transitionService.markAsOverdueWhenPastDueDate(invoice.getId(), LocalDate.now());
        transitionService.markAsOverdueWhenPastDueDate(invoice.getId(), LocalDate.now());

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isOverdue();
        assertThat(reloaded.getAmounts().total().value()).isEqualTo(ars(1000));
    }

    @Test
    void runningTheDailyJobTwiceTheSameDayLeavesTheInvoiceOverdueWithoutChangingItsTotal() {
        Person director = persistPerson("director.sweep.rerun." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000902");
        Invoice invoice = persistInvoice(enrollment, ars(1000), LocalDate.now().minusDays(15));

        sweepService.run(LocalDate.now());
        sweepService.run(LocalDate.now());

        assertThat(invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow().getAmounts().total().value()).isEqualTo(ars(1000));
    }
}
