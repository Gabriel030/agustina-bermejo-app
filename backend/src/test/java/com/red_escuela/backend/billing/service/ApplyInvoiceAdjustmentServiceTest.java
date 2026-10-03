package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.CreateInvoiceAdjustmentRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentValueRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.usecase.invoice.ApplyInvoiceAdjustmentCommand;
import com.red_escuela.backend.billing.usecase.invoice.ApplyInvoiceAdjustmentUseCase;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ApplyInvoiceAdjustmentServiceTest extends AbstractIntegrationTest {
    @Autowired
    private ApplyInvoiceAdjustmentUseCase applyInvoiceAdjustmentUseCase;

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

    private Invoice persistInvoice(InvoiceStatus status) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson("adjustment.student." + System.nanoTime()));
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
        enrollment = courseEnrollmentRepository.save(enrollment);

        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(BigDecimal.valueOf(10000));
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private ApplyInvoiceAdjustmentCommand surchargeOf(Invoice invoice) {
        return new ApplyInvoiceAdjustmentCommand(invoice.getId(), new CreateInvoiceAdjustmentRequest(
                InvoiceAdjustmentType.SURCHARGE, new MonetaryAdjustmentValueRequest(BigDecimal.valueOf(500), null), "Recargo manual"));
    }

    @Test
    void aPaidInvoiceDoesNotAcceptAdjustments() {
        Person director = persistPerson("director.adjustment1." + System.nanoTime());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null));
        Invoice invoice = persistInvoice(InvoiceStatus.PAID);

        assertThatThrownBy(() -> applyInvoiceAdjustmentUseCase.apply(surchargeOf(invoice)))
                .isInstanceOf(InvoiceNotPayableException.class);

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(reloaded.getAdjustments()).isEmpty();
    }

    @Test
    void aPendingInvoiceStillAcceptsAdjustments() {
        Person director = persistPerson("director.adjustment2." + System.nanoTime());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null));
        Invoice invoice = persistInvoice(InvoiceStatus.PENDING);

        applyInvoiceAdjustmentUseCase.apply(surchargeOf(invoice));

        assertThat(invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow().getAmountDue())
                .isEqualByComparingTo("10500");
    }
}
