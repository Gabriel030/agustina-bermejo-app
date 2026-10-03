package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.usecase.dashboard.GetFinancialDashboardUseCase;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.repository.AddressRepository;
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
class FinancialDashboardServiceTest extends AbstractIntegrationTest {
    @Autowired
    private GetFinancialDashboardUseCase getFinancialDashboardUseCase;

    @Autowired
    private ListDebtorsUseCase listDebtorsUseCase;

    @Autowired
    private RegisterPaymentUseCase registerPaymentUseCase;

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

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private Student persistStudent(String username) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(username));
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

    private Invoice persistInvoice(CourseEnrollment enrollment, BigDecimal amount, InvoiceStatus status, YearMonth period) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(period);
        invoice.setDueDate(period.atDay(10));
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private void pay(Invoice invoice, BigDecimal amount, Person admin) {
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), amount)),
                PaymentMethod.CASH, false, null, null, null, null), admin);
    }

    @Test
    void debtorTotalIncludesTheLateFeeAndSubtractsPartialPayments() {
        Person admin = persistPerson("admin.dashboard1." + System.nanoTime());
        Student student = persistStudent("dashboard.debtor1." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(2));
        invoice.markOverdue(new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.10")), "Recargo por mora");
        invoice = invoiceRepository.save(invoice);
        pay(invoice, BigDecimal.valueOf(3000), admin);

        DebtorSummaryResponse debtor = listDebtorsUseCase.list("DEBT_AMOUNT").stream()
                .filter(summary -> summary.studentId().equals(student.getId()))
                .findFirst().orElseThrow();

        assertThat(debtor.totalDebt()).isEqualByComparingTo("8000");
    }

    @Test
    void totalDebtAddsLateFeesSubtractsDiscountsAndPartialPaymentsOfOverdueInstallments() {
        Person admin = persistPerson("admin.dashboard3." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("dashboard.total." + System.nanoTime()));
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(2));
        invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT,
                new MonetaryAdjustmentValue.FixedAmount(BigDecimal.valueOf(500)), "Descuento", admin);
        invoice.markOverdue(new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.10")), "Recargo por mora");
        invoice = invoiceRepository.save(invoice);
        pay(invoice, BigDecimal.valueOf(2000), admin);
        persistInvoice(enrollment, BigDecimal.valueOf(7000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(1));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(after.totalDebt().subtract(before.totalDebt())).isEqualByComparingTo("8450");
    }

    @Test
    void monthlyProjectedAndCollectedUseTheNetAmountAndTheMoneyActuallyApplied() {
        Person admin = persistPerson("admin.dashboard2." + System.nanoTime());
        Student student = persistStudent("dashboard.month1." + System.nanoTime());
        CourseEnrollment scholarshipEnrollment = persistCourseEnrollment(student);
        CourseEnrollment regularEnrollment = persistCourseEnrollment(student);
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        Invoice withScholarship = persistInvoice(scholarshipEnrollment, BigDecimal.valueOf(10000), InvoiceStatus.PENDING, YearMonth.now());
        withScholarship.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP,
                new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.50")), "Beca 50%", admin);
        invoiceRepository.save(withScholarship);
        Invoice partiallyPaid = persistInvoice(regularEnrollment,BigDecimal.valueOf(8000), InvoiceStatus.PENDING, YearMonth.now());
        pay(partiallyPaid, BigDecimal.valueOf(3000), admin);

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(after.monthlyProjected().subtract(before.monthlyProjected())).isEqualByComparingTo("13000");
        assertThat(after.monthlyCollected().subtract(before.monthlyCollected())).isEqualByComparingTo("3000");
        assertThat(after.monthlyDebt().subtract(before.monthlyDebt())).isEqualByComparingTo("10000");
    }

    @Test
    void studentsUpToDateOnlyCountsStudentsWithAnActiveEnrollmentAndNoOverdueDebt() {
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        persistStudent("dashboard.noenrollment." + System.nanoTime());
        persistCourseEnrollment(persistStudent("dashboard.uptodate." + System.nanoTime()));
        CourseEnrollment indebted = persistCourseEnrollment(persistStudent("dashboard.indebted." + System.nanoTime()));
        persistInvoice(indebted, BigDecimal.valueOf(5000), InvoiceStatus.OVERDUE, YearMonth.now().minusMonths(1));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(after.studentsUpToDate() - before.studentsUpToDate()).isEqualTo(1);
        assertThat(after.studentsInDebt() - before.studentsInDebt()).isEqualTo(1);
    }
}
