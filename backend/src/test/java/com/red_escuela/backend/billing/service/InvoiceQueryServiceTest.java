package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceQueryServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceQueryService invoiceQueryService;

    @Autowired
    private com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

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
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.from(dueDate));
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    @Test
    void breakdownIncludesAdjustmentsPaymentsAndTheDescriptionOfTheCurrentPriceRevision() {
        Person director = persistPerson("director.breakdown1." + System.nanoTime());
        Person admin = persistPerson("admin.breakdown1." + System.nanoTime());
        Course course = persistCourse();

        PriceRevision priceRevision = new PriceRevision();
        priceRevision.setCourse(course);
        priceRevision.setFeeType(FeeType.RECURRING_FEE);
        priceRevision.setDescription("Cuota mensual 2026");
        priceRevision.setMonthlyAmount(ars(10000));
        priceRevision.setEffectiveFrom(LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(director);
        priceRevisionRepository.save(priceRevision);

        CourseEnrollment enrollment = persistCourseEnrollment("99000401", course);
        Invoice invoice = persistInvoice(enrollment, ars(10000), LocalDate.now());
        invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustment.Proportional(Percentage.ofFraction(BigDecimal.valueOf(0.10))),
                "Descuento de prueba", director);
        invoiceRepository.save(invoice);
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), ars(4000))),
                PaymentMethod.CASH, false, null, null, null, null), admin);

        PriceBreakdownResponse breakdown = invoiceQueryService.priceBreakdown(invoice.getId());

        assertThat(breakdown.priceRevisionDescription()).isEqualTo("Cuota mensual 2026");
        assertThat(breakdown.baseAmount()).isEqualTo(ars(10000));
        assertThat(breakdown.adjustments()).hasSize(1);
        assertThat(breakdown.adjustments().get(0).type()).isEqualTo("DISCOUNT");
        assertThat(breakdown.amountDue()).isEqualTo(ars(9000));
        assertThat(breakdown.paymentsApplied()).hasSize(1);
        assertThat(breakdown.remainingBalance()).isEqualTo(ars(5000));
        assertThat(breakdown.status()).isEqualTo("PENDING");
    }
}
