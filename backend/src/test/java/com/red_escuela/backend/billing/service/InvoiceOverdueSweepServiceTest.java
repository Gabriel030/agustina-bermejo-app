package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustment;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceOverdueSweepServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceOverdueSweepService sweepService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CollectionsPolicyRepository collectionsPolicyRepository;

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

    private Invoice persistInvoice(CourseEnrollment enrollment, BigDecimal amount, LocalDate dueDate) {
        return persistInvoice(enrollment, amount, dueDate, YearMonth.from(dueDate));
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, BigDecimal amount, LocalDate dueDate, YearMonth period) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private CollectionsPolicy persistPolicy(Person createdBy, int gracePeriodDays, BigDecimal surchargePercentage) {
        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(gracePeriodDays);
        policy.setSurchargePercentage(surchargePercentage);
        policy.setSuspensionAfterDays(30);
        policy.setSuspensionAfterInvoiceCount(2);
        policy.setWithdrawalAfterDays(60);
        policy.setWithdrawalAfterInvoiceCount(3);
        policy.setEffectiveFrom(LocalDate.now().minusYears(1));
        policy.setCreatedBy(createdBy);
        return collectionsPolicyRepository.save(policy);
    }

    @Test
    void installmentOverdueForMoreThanTenDaysBecomesOverdueWithTheCurrentSurcharge() {
        Person director = persistPerson("director.sweep1." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000001");
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(11));

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(reloaded.getAdjustments()).hasSize(1);
        assertThat(reloaded.getAdjustments().get(0).getType()).isEqualTo(InvoiceAdjustmentType.SURCHARGE);
        assertThat(reloaded.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(1100));
    }

    @Test
    void installmentOverdueForExactlyTenDaysDoesNotBecomeOverdueYet() {
        Person director = persistPerson("director.sweep2." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000002");
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(10));

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(reloaded.getAdjustments()).isEmpty();
    }

    @Test
    void sweepCrashMidRowDoesNotLeaveInconsistentState() {
        Person director = persistPerson("director.sweep3." + System.nanoTime());
        CollectionsPolicy policy = persistPolicy(director, 10, BigDecimal.valueOf(1.5));
        CourseEnrollment enrollment = persistCourseEnrollment("99000003");
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(11));

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(reloaded.getAdjustments()).isEmpty();

        var logs = batchExecutionLogRepository.findByJobNameOrderByRanAtDesc("InvoiceOverdueSweepService");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(BatchExecutionStatus.FAILED);
        assertThat(logs.get(0).getFailedCount()).isEqualTo(1);
        assertThat(logs.get(0).getProcessedCount()).isEqualTo(0);
    }

    @Test
    void dailyProcessMustReviewFourHundredOverdueInstallmentsAndNoneIsLeftUnprocessed() {
        Person director = persistPerson("director.sweep5." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000005");

        int total = 400;
        for (int i = 0; i < total; i++) {
            persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusMonths(i).minusDays(11), YearMonth.now().minusMonths(i));
        }

        sweepService.run(LocalDate.now());

        long overdueCount = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .count();
        assertThat(overdueCount).isEqualTo(total);
    }

    @Test
    void lateSurchargeIsCalculatedOnTheAmountWithDiscountAlreadyAppliedNotOnTheListPrice() {
        Person director = persistPerson("director.sweep4." + System.nanoTime());
        persistPolicy(director, 10, BigDecimal.valueOf(0.10));
        CourseEnrollment enrollment = persistCourseEnrollment("99000004");
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(11));
        invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustmentValue.PercentageAmount(BigDecimal.valueOf(0.90)),
                "Beca 90%", director);
        invoiceRepository.save(invoice);

        sweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        List<InvoiceAdjustment> adjustments = reloaded.getAdjustments();
        assertThat(adjustments).hasSize(2);
        var surcharge = adjustments.stream().filter(a -> a.getType() == InvoiceAdjustmentType.SURCHARGE).findFirst().orElseThrow();
        assertThat(surcharge.getResolvedBase()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(surcharge.signedAmount()).isEqualByComparingTo(BigDecimal.valueOf(10.00).setScale(2));
    }
}
