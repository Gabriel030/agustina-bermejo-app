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
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.exception.GatewayCurrencyNotSupportedException;
import com.red_escuela.backend.billing.gateway.exception.PaymentGatewayUnavailableException;
import com.red_escuela.backend.billing.usecase.paymentgateway.CreateMpPreferenceUseCase;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.usecase.dashboard.GetFinancialDashboardUseCase;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
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
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
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

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, InvoicePaymentStatus status) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(1)).dueOn(LocalDate.now().minusDays(20)).withBaseAmount(amount).inState(status).build();
        return invoice;
    }

    private static FinancialDashboardResponse.CurrencyTotals totalsOf(FinancialDashboardResponse response, Currency currency) {
        return response.totalsByCurrency().stream()
                .filter(totals -> totals.totalDebt().currency() == currency)
                .findFirst()
                .orElse(new FinancialDashboardResponse.CurrencyTotals(
                        Money.zero(currency), Money.zero(currency), Money.zero(currency), Money.zero(currency)));
    }

    @Test
    @Tag("D-226")
    void creatingAnOnlinePaymentPreferenceIsRejectedWhileTheGatewayIsDisabled() {
        Student student = persistStudent("gateway.student." + System.nanoTime());
        Invoice invoice = persistInvoice(persistCourseEnrollment(student, Currency.ARS), ars(10000), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> createMpPreferenceUseCase.create(invoice.getId(), student.getId())))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    @Tag("D-097")
    void anInstallmentInUsdPaidInUsdIsSettled() {
        Person admin = persistPerson("admin.usd1." + System.nanoTime());
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent("usd.student1." + System.nanoTime()), Currency.USD);
        Invoice invoice = persistInvoice(enrollment, usd(100), InvoicePaymentStatus.PENDING);

        BillingTestFixtures.registerApprovedBankTransferPayment(invoice.getId(), admin.getId());

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isPaid();
    }

    @Test
    @Tag("D-086")
    void mercadoPagoIsNotOfferedForAnInstallmentInUsdAndStaffRegistersItManually() {
        Person admin = persistPerson("admin.usd4." + System.nanoTime());
        Student student = persistStudent("usd.student4." + System.nanoTime());
        Invoice invoice = persistInvoice(persistCourseEnrollment(student, Currency.USD), usd(100), InvoicePaymentStatus.PENDING);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> createMpPreferenceUseCase.create(invoice.getId(), student.getId())))
                .isInstanceOf(GatewayCurrencyNotSupportedException.class);
        BillingTestFixtures.registerApprovedBankTransferPayment(invoice.getId(), admin.getId());

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(jdbc.queryForList("select amount, amount_currency from payment_allocation where invoice_id = ?", invoice.getId()))
                .singleElement().satisfies(row -> {
                    assertThat((java.math.BigDecimal) row.get("amount")).isEqualByComparingTo("100");
                    assertThat(row.get("amount_currency")).isEqualTo("USD");
                });
    }

    @Test
    @Tag("D-097")
    void theAccountStatementWithDebtsInBothCurrenciesShowsTwoTotals() {
        FinancialDashboardResponse before = getFinancialDashboardUseCase.execute();
        Student student = persistStudent("usd.student3." + System.nanoTime());
        persistInvoice(persistCourseEnrollment(student, Currency.ARS), ars(10000), InvoicePaymentStatus.OVERDUE);
        persistInvoice(persistCourseEnrollment(student, Currency.USD), usd(100), InvoicePaymentStatus.OVERDUE);

        FinancialDashboardResponse after = getFinancialDashboardUseCase.execute();

        assertThat(totalsOf(after, Currency.ARS).totalDebt().minus(totalsOf(before, Currency.ARS).totalDebt())).isEqualTo(ars(10000));
        assertThat(totalsOf(after, Currency.USD).totalDebt().minus(totalsOf(before, Currency.USD).totalDebt())).isEqualTo(usd(100));
        List<DebtorSummaryResponse> debtRows = listDebtorsUseCase.list("DEBT_AMOUNT").stream()
                .filter(row -> row.studentId().equals(student.getId()))
                .toList();
        assertThat(debtRows).extracting(DebtorSummaryResponse::totalDebt).containsExactlyInAnyOrder(ars(10000), usd(100));
    }
}
