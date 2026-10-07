package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
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
import com.red_escuela.backend.billing.exception.GatewayCurrencyNotSupportedException;
import com.red_escuela.backend.billing.usecase.paymentgateway.CreateMpPreferenceUseCase;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentAllocationRepository;
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
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UsdBillingTest extends AbstractIntegrationTest {

    @Autowired
    private RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private CreateMpPreferenceUseCase createMpPreferenceUseCase;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private GetFinancialDashboardUseCase getFinancialDashboardUseCase;

    @Autowired
    private ListDebtorsUseCase listDebtorsUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

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

    private CourseEnrollment persistCourseEnrollment(Student student, Currency currency) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        Course course = new Course();
        course.setName("Curso " + currency + " " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course.setCurrency(currency);
        course = courseRepository.save(course);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, InvoiceStatus status) {
        Invoice invoice = BillingTestFixtures.invoice().build();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        invoice.setPeriod(YearMonth.now().minusMonths(1));
        invoice.setDueDate(LocalDate.now().minusDays(20));
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private RegisterPaymentRequest paymentOf(Invoice invoice, Money amount) {
        return new RegisterPaymentRequest(List.of(new PaymentAllocationRequest(invoice.getId(), amount)),
                PaymentMethod.CASH, false, null, null, null, null);
    }

    private static FinancialDashboardResponse.CurrencyTotals totalsOf(FinancialDashboardResponse response, Currency currency) {
        return response.totalsByCurrency().stream()
                .filter(totals -> totals.totalDebt().currency() == currency)
                .findFirst()
                .orElse(new FinancialDashboardResponse.CurrencyTotals(
                        Money.zero(currency), Money.zero(currency), Money.zero(currency), Money.zero(currency)));
    }

    @Test
    @Tag("D-097")
    void anInstallmentInUsdPaidInUsdIsSettled() {
        Person admin = persistPerson("admin.usd1." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("usd.student1." + System.nanoTime()), Currency.USD);
        Invoice invoice = persistInvoice(enrollment, usd(100), InvoiceStatus.PENDING);

        registerPaymentUseCase.register(paymentOf(invoice, usd(100)), admin);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isPaid();
    }

    @Test
    @Tag("D-086")
    void mercadoPagoIsNotOfferedForAnInstallmentInUsdAndStaffRegistersItManually() {
        Person admin = persistPerson("admin.usd4." + System.nanoTime());
        Student student = persistStudent("usd.student4." + System.nanoTime());
        Invoice invoice = persistInvoice(persistCourseEnrollment(student, Currency.USD), usd(100), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> createMpPreferenceUseCase.create(invoice.getId(), student.getId())))
                .isInstanceOf(GatewayCurrencyNotSupportedException.class);
        registerPaymentUseCase.register(paymentOf(invoice, usd(100)), admin);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(paymentAllocationRepository.findByInvoiceId(invoice.getId()))
                .singleElement().satisfies(allocation -> assertThat(allocation.getAmountApplied()).isEqualTo(usd(100)));
    }

    @Test
    @Tag("D-097")
    void aPaymentInArsIsNotAppliedToAnInstallmentInUsd() {
        Person admin = persistPerson("admin.usd2." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("usd.student2." + System.nanoTime()), Currency.USD);
        Invoice invoice = persistInvoice(enrollment, usd(100), InvoiceStatus.PENDING);

        assertThatThrownBy(() -> registerPaymentUseCase.register(paymentOf(invoice, ars(100)), admin))
                .isInstanceOf(CurrencyMismatchException.class);

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();
        assertThat(paymentAllocationRepository.findByInvoiceId(invoice.getId())).isEmpty();
    }

    @Test
    @Tag("D-097")
    void theAccountStatementWithDebtsInBothCurrenciesShowsTwoTotals() {
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();
        Student student = persistStudent("usd.student3." + System.nanoTime());
        persistInvoice(persistCourseEnrollment(student, Currency.ARS), ars(10000), InvoiceStatus.OVERDUE);
        persistInvoice(persistCourseEnrollment(student, Currency.USD), usd(100), InvoiceStatus.OVERDUE);

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(totalsOf(after, Currency.ARS).totalDebt().minus(totalsOf(before, Currency.ARS).totalDebt())).isEqualTo(ars(10000));
        assertThat(totalsOf(after, Currency.USD).totalDebt().minus(totalsOf(before, Currency.USD).totalDebt())).isEqualTo(usd(100));
        List<DebtorSummaryResponse> debtRows = listDebtorsUseCase.list("DEBT_AMOUNT").stream()
                .filter(row -> row.studentId().equals(student.getId()))
                .toList();
        assertThat(debtRows).extracting(DebtorSummaryResponse::totalDebt).containsExactlyInAnyOrder(ars(10000), usd(100));
    }
}
