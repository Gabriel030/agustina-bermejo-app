package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
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
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.usecase.dashboard.GetFinancialDashboardUseCase;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.repository.AddressRepository;
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
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

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

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, InvoiceStatus status, YearMonth period) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        invoice.setPeriod(period);
        invoice.setDueDate(period.atDay(10));
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private static FinancialDashboardResponse.CurrencyTotals arsTotals(FinancialDashboardResponse response) {
        return response.totalsByCurrency().stream()
                .filter(totals -> totals.totalDebt().currency() == Currency.ARS)
                .findFirst().orElseThrow();
    }

    private void pay(Invoice invoice, Money amount, Person admin) {
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), amount)),
                PaymentMethod.CASH, false, null, null, null, null), admin);
    }

    @Test
    void debtorTotalIncludesTheLateFeeAndSubtractsPartialPayments() {
        Person admin = persistPerson("admin.dashboard1." + System.nanoTime());
        Student student = persistStudent("dashboard.debtor1." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(2));
        invoice.markOverdue(new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.10"))), "Recargo por mora");
        invoice = invoiceRepository.save(invoice);
        pay(invoice, ars(3000), admin);

        DebtorSummaryResponse debtor = listDebtorsUseCase.list("DEBT_AMOUNT").stream()
                .filter(summary -> summary.studentId().equals(student.getId()))
                .findFirst().orElseThrow();

        assertThat(debtor.totalDebt()).isEqualTo(ars(8000));
    }

    @Test
    void totalDebtAddsLateFeesSubtractsDiscountsAndPartialPaymentsOfOverdueInstallments() {
        Person admin = persistPerson("admin.dashboard3." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("dashboard.total." + System.nanoTime()));
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        Invoice invoice = persistInvoice(enrollment, ars(10000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(2));
        invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT,
                new MonetaryAdjustment.Nominal(ars(500)), "Descuento", admin);
        invoice.markOverdue(new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.10"))), "Recargo por mora");
        invoice = invoiceRepository.save(invoice);
        pay(invoice, ars(2000), admin);
        persistInvoice(enrollment, ars(7000), InvoiceStatus.PENDING, YearMonth.now().minusMonths(1));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(arsTotals(after).totalDebt().minus(arsTotals(before).totalDebt())).isEqualTo(ars(8450));
    }

    @Test
    void monthlyProjectedAndCollectedUseTheNetAmountAndTheMoneyActuallyApplied() {
        Person admin = persistPerson("admin.dashboard2." + System.nanoTime());
        Student student = persistStudent("dashboard.month1." + System.nanoTime());
        CourseEnrollment scholarshipEnrollment = persistCourseEnrollment(student);
        CourseEnrollment regularEnrollment = persistCourseEnrollment(student);
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        Invoice withScholarship = persistInvoice(scholarshipEnrollment, ars(10000), InvoiceStatus.PENDING, YearMonth.now());
        withScholarship.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP,
                new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.50"))), "Beca 50%", admin);
        invoiceRepository.save(withScholarship);
        Invoice partiallyPaid = persistInvoice(regularEnrollment,ars(8000), InvoiceStatus.PENDING, YearMonth.now());
        pay(partiallyPaid, ars(3000), admin);

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(arsTotals(after).monthlyProjected().minus(arsTotals(before).monthlyProjected())).isEqualTo(ars(13000));
        assertThat(arsTotals(after).monthlyCollected().minus(arsTotals(before).monthlyCollected())).isEqualTo(ars(3000));
        assertThat(arsTotals(after).monthlyDebt().minus(arsTotals(before).monthlyDebt())).isEqualTo(ars(10000));
    }

    @Test
    void studentsUpToDateOnlyCountsStudentsWithAnActiveEnrollmentAndNoOverdueDebt() {
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        persistStudent("dashboard.noenrollment." + System.nanoTime());
        persistCourseEnrollment(persistStudent("dashboard.uptodate." + System.nanoTime()));
        CourseEnrollment indebted = persistCourseEnrollment(persistStudent("dashboard.indebted." + System.nanoTime()));
        persistInvoice(indebted, ars(5000), InvoiceStatus.OVERDUE, YearMonth.now().minusMonths(1));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(after.studentsUpToDate() - before.studentsUpToDate()).isEqualTo(1);
        assertThat(after.studentsInDebt() - before.studentsInDebt()).isEqualTo(1);
    }
}
