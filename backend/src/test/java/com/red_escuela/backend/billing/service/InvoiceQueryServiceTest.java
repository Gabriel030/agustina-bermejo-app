package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceQueryServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceQueryService invoiceQueryService;

    @Autowired
    private JdbcTemplate jdbc;

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
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private CourseEnrollment persistCourseEnrollment(String dni, Course course) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private Course persistCourse() {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course);
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, Money amount, LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(amount).build();
    }

    @Test
    @Tag("D-035")
    @Tag("D-214")
    void breakdownOfAnInvoiceIssuedBeforeAPriceIncreaseKeepsTheLineVersionAndItsAmount() {
        Person director = persistPerson("director.breakdown30." + System.nanoTime());
        Course course = persistCourse();
        Long firstVersion = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota mensual 2026", ars(10000), LocalDate.now().minusYears(1), director.getId());
        CourseEnrollment enrollment = persistCourseEnrollment("99000430", course);
        Invoice invoice = persistInvoice(enrollment, ars(10000), LocalDate.now().plusDays(10));
        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota mensual 2026 con aumento", ars(12000), LocalDate.now().minusDays(1), director.getId());

        PriceBreakdownResponse breakdown = invoiceQueryService.priceBreakdown(invoice.getId());

        assertThat(invoice.getPrimaryLine().getPriceVersionId()).isEqualTo(firstVersion);
        assertThat(breakdown.priceRevisionDescription()).isEqualTo("Cuota mensual 2026");
        assertThat(breakdown.baseAmount()).isEqualTo(ars(10000));
    }

    @Test
    @Tag("D-376")
    void breakdownIncludesPaymentsAndTheDescriptionOfTheCurrentPriceRevision() {
        Person director = persistPerson("director.breakdown1." + System.nanoTime());
        Course course = persistCourse();

        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota mensual 2026", ars(10000), LocalDate.now().minusYears(1), director.getId());

        CourseEnrollment enrollment = persistCourseEnrollment("99000401", course);
        Invoice invoice = persistInvoice(enrollment, ars(10000), LocalDate.now());
        BillingTestFixtures.payInvoiceInFull(invoice);

        PriceBreakdownResponse breakdown = invoiceQueryService.priceBreakdown(invoice.getId());

        assertThat(breakdown.priceRevisionDescription()).isEqualTo("Cuota mensual 2026");
        assertThat(breakdown.baseAmount()).isEqualTo(ars(10000));
        assertThat(breakdown.adjustments()).isEmpty();
        assertThat(breakdown.amountDue()).isEqualTo(ars(10000));
        assertThat(breakdown.paymentsApplied()).singleElement().satisfies(payment -> {
            assertThat(payment.method()).isEqualTo("CASH");
            assertThat(payment.amount()).isEqualTo(ars(10000));
        });
        assertThat(breakdown.remainingBalance()).isEqualTo(ars(0));
        assertThat(breakdown.status()).isEqualTo("PAID");
    }

    private void withSourceUpdate(String sql, Runnable assertion, String restoreSql) {
        runInReplicaSession(sql);
        try {
            assertion.run();
        } finally {
            runInReplicaSession(restoreSql);
        }
    }

    private void runInReplicaSession(String sql) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set session_replication_role = replica");
                statement.execute(sql);
                statement.execute("set session_replication_role = origin");
            }
            return null;
        });
    }

    private Invoice paidInvoiceOfANewEnrollment(String dni) {
        Person director = persistPerson("director.integrity." + dni + System.nanoTime());
        Course course = persistCourse();
        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota", ars(10000), LocalDate.now().minusYears(1), director.getId());
        CourseEnrollment enrollment = persistCourseEnrollment(dni, course);
        Invoice invoice = persistInvoice(enrollment, ars(10000), LocalDate.now());
        BillingTestFixtures.payInvoiceInFull(invoice);
        return invoice;
    }

    private Long sourceIdOf(Invoice invoice) {
        return jdbc.queryForObject("select s.id from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ?", Long.class, invoice.getId());
    }

    @Test
    @Tag("D-376")
    void aSourceWhosePaymentDoesNotExistBreaksTheBreakdownWithAnIntegrityError() {
        Invoice invoice = paidInvoiceOfANewEnrollment("99000411");
        Long sourceId = sourceIdOf(invoice);
        Long paymentId = jdbc.queryForObject("select payment_id from payment_allocation_source where id = ?", Long.class, sourceId);

        withSourceUpdate("update payment_allocation_source set payment_id = 999999999 where id = " + sourceId, () ->
                assertThatThrownBy(() -> invoiceQueryService.priceBreakdown(invoice.getId()))
                        .isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentSourceIntegrityException.class)
                        .hasMessageContaining("999999999").hasMessageContaining("does not exist"),
                "update payment_allocation_source set payment_id = " + paymentId + " where id = " + sourceId);
    }

    @Test
    @Tag("D-376")
    void aSourceWhosePaymentBelongsToAnotherAccountBreaksTheBreakdownWithAnIntegrityError() {
        Invoice invoice = paidInvoiceOfANewEnrollment("99000412");
        Invoice other = paidInvoiceOfANewEnrollment("99000413");
        Long sourceId = sourceIdOf(invoice);
        Long paymentId = jdbc.queryForObject("select payment_id from payment_allocation_source where id = ?", Long.class, sourceId);
        Long otherPaymentId = BillingTestFixtures.seedApprovedPaymentRow(other.getStudentCurrentAccountId());

        withSourceUpdate("update payment_allocation_source set payment_id = " + otherPaymentId + " where id = " + sourceId, () ->
                assertThatThrownBy(() -> invoiceQueryService.priceBreakdown(invoice.getId()))
                        .isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentSourceIntegrityException.class)
                        .hasMessageContaining("belongs to another account"),
                "update payment_allocation_source set payment_id = " + paymentId + " where id = " + sourceId);
    }

    @Test
    @Tag("D-376")
    void aSourceWhoseCreditNoteDoesNotExistBreaksTheBreakdownWithAnIntegrityError() {
        Invoice invoice = paidInvoiceOfANewEnrollment("99000414");
        Long sourceId = sourceIdOf(invoice);
        Long paymentId = jdbc.queryForObject("select payment_id from payment_allocation_source where id = ?", Long.class, sourceId);

        withSourceUpdate("update payment_allocation_source set payment_id = null, credit_note_id = 999999999 where id = " + sourceId, () ->
                assertThatThrownBy(() -> invoiceQueryService.priceBreakdown(invoice.getId()))
                        .isInstanceOf(com.red_escuela.backend.billing.note.exception.CreditNoteSourceIntegrityException.class)
                        .hasMessageContaining("999999999"),
                "update payment_allocation_source set payment_id = " + paymentId + ", credit_note_id = null where id = " + sourceId);
    }
}
