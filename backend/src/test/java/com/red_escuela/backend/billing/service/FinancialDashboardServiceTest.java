package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
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
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
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

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, InvoicePaymentStatus status, YearMonth period) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(period).dueOn(period.atDay(10)).withBaseAmount(amount).inState(status).build();
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
    void debtorTotalIsTheOutstandingAmountOfTheOverdueInvoicesAndPaidOnesAreLeftOut() {
        Student student = persistStudent("dashboard.debtor1." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.OVERDUE, YearMonth.now().minusMonths(2));
        persistInvoice(enrollment, ars(8000), InvoicePaymentStatus.PAID, YearMonth.now().minusMonths(3));

        DebtorSummaryResponse debtor = listDebtorsUseCase.list("DEBT_AMOUNT").stream()
                .filter(summary -> summary.studentId().equals(student.getId()))
                .findFirst().orElseThrow();

        assertThat(debtor.totalDebt()).isEqualTo(ars(10000));
    }

    @Test
    void totalDebtAddsTheOutstandingAmountOfTheOverdueInvoicesOnly() {
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("dashboard.total." + System.nanoTime()));
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        persistInvoice(enrollment, ars(10000), InvoicePaymentStatus.OVERDUE, YearMonth.now().minusMonths(2));
        persistInvoice(enrollment, ars(7000), InvoicePaymentStatus.PENDING, YearMonth.now().minusMonths(1));
        persistInvoice(enrollment, ars(5000), InvoicePaymentStatus.PAID, YearMonth.now().minusMonths(3));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(arsTotals(after).totalDebt().minus(arsTotals(before).totalDebt())).isEqualTo(ars(10000));
    }

    @Test
    void monthlyProjectedAndCollectedUseTheInvoiceTotalAndTheMoneyActuallyPaid() {
        Student student = persistStudent("dashboard.month1." + System.nanoTime());
        CourseEnrollment firstEnrollment = persistCourseEnrollment(student);
        CourseEnrollment secondEnrollment = persistCourseEnrollment(student);
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        persistInvoice(firstEnrollment, ars(10000), InvoicePaymentStatus.PENDING, YearMonth.now());
        persistInvoice(secondEnrollment, ars(8000), InvoicePaymentStatus.PAID, YearMonth.now());

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(arsTotals(after).monthlyProjected().minus(arsTotals(before).monthlyProjected())).isEqualTo(ars(18000));
        assertThat(arsTotals(after).monthlyCollected().minus(arsTotals(before).monthlyCollected())).isEqualTo(ars(8000));
        assertThat(arsTotals(after).monthlyDebt().minus(arsTotals(before).monthlyDebt())).isEqualTo(ars(10000));
    }

    @Test
    void studentsUpToDateOnlyCountsStudentsWithAnActiveEnrollmentAndNoOverdueDebt() {
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();

        persistStudent("dashboard.noenrollment." + System.nanoTime());
        persistCourseEnrollment(persistStudent("dashboard.uptodate." + System.nanoTime()));
        CourseEnrollment indebted = persistCourseEnrollment(persistStudent("dashboard.indebted." + System.nanoTime()));
        persistInvoice(indebted, ars(5000), InvoicePaymentStatus.OVERDUE, YearMonth.now().minusMonths(1));

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(after.studentsUpToDate() - before.studentsUpToDate()).isEqualTo(1);
        assertThat(after.studentsInDebt() - before.studentsInDebt()).isEqualTo(1);
    }
}
