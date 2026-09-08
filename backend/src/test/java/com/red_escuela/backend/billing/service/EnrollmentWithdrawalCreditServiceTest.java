package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import com.red_escuela.backend.service.CourseEnrollmentService;
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

// Fase 6.6: baja voluntaria cancela cuotas PENDING y acredita cuotas PAID de un período futuro;
// una baja por mora no toca ninguna de las dos cosas.
@SpringBootTest
@ActiveProfiles("test")
class EnrollmentWithdrawalCreditServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CreditNoteRepository creditNoteRepository;

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

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
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

    @Test
    void bajaVoluntariaCancelaLasCuotasPendientes() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000001." + System.nanoTime());
        Invoice pendiente = persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, YearMonth.now().plusMonths(1));

        authenticateAs(persistPerson("preceptor.baja1." + System.nanoTime()));
        courseEnrollmentService.darDeBaja(enrollment.getId());

        Invoice reloaded = invoiceRepository.findById(pendiente.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(reloaded.getCancelledReason()).isNotBlank();
    }

    @Test
    void bajaVoluntariaConCuotasFuturasYaPagasGeneraNotaDeCreditoPorEsePeriodo() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000002." + System.nanoTime());
        // Cuota del período corriente ya paga: NO genera crédito (ese mes ya se consideró usado).
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now());
        // Cuota de un período futuro ya paga (alumno adelantó pagos): sí genera crédito.
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now().plusMonths(2));

        authenticateAs(persistPerson("preceptor.baja2." + System.nanoTime()));
        courseEnrollmentService.darDeBaja(enrollment.getId());

        CreditNote creditNote = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId()).get(0);
        assertThat(creditNote.getReasonType()).isEqualTo(CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED);
        // Solo la cuota futura (futuraPaga): la del período corriente ya se consideró "usada" por
        // ese mes, no genera crédito aunque también esté PAID (ver bajaVoluntariaConCuotasFuturasYaPagasGeneraNotaDeCreditoPorEsePeriodo).
        assertThat(creditNote.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    // Test 71 (spec): un bloque de varias cuotas futuras ya pagas queda trazado completo en
    // sourceInvoices, no solo el monto total agregado.
    @Test
    void bajaVoluntariaConVariasCuotasFuturasYaPagasLasTrazaTodasEnSourceInvoices() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000004." + System.nanoTime());
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now());
        Invoice futura1 = persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now().plusMonths(1));
        Invoice futura2 = persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now().plusMonths(2));
        Invoice futura3 = persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now().plusMonths(3));

        authenticateAs(persistPerson("preceptor.baja4." + System.nanoTime()));
        courseEnrollmentService.darDeBaja(enrollment.getId());

        Long creditNoteId = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId()).get(0).getId();
        CreditNote creditNote = creditNoteRepository.findByIdWithSourceInvoices(creditNoteId).orElseThrow();
        assertThat(creditNote.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(creditNote.getSourceInvoices()).extracting(Invoice::getId)
                .containsExactlyInAnyOrder(futura1.getId(), futura2.getId(), futura3.getId());
    }

    // Test 72 (spec): sin ninguna cuota PAID de un período futuro, la baja voluntaria no genera
    // ninguna CreditNote — solo cancela las PENDING (ya cubierto por bajaVoluntariaCancelaLasCuotasPendientes).
    @Test
    void bajaVoluntariaSinCuotasFuturasYaPagasNoGeneraNingunaNotaDeCredito() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000005." + System.nanoTime());
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, YearMonth.now().plusMonths(1));
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now());

        authenticateAs(persistPerson("preceptor.baja5." + System.nanoTime()));
        courseEnrollmentService.darDeBaja(enrollment.getId());

        assertThat(creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId())).isEmpty();
    }

    @Test
    void bajaPorMoraNoCancelaCuotasNiGeneraCredito() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000003." + System.nanoTime());
        Invoice pendiente = persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PENDING, YearMonth.now().plusMonths(1));
        persistInvoice(enrollment, BigDecimal.valueOf(1000), InvoiceStatus.PAID, YearMonth.now().plusMonths(2));
        enrollment.setBillingStanding(EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        courseEnrollmentRepository.save(enrollment);

        // Sin autenticar a propósito: la baja automática por mora corre sin JWT (job en
        // background) — si esto intentara resolver un actor igual, fallaría acá con NPE.
        courseEnrollmentService.darDeBaja(enrollment.getId());

        assertThat(invoiceRepository.findById(pendiente.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId())).isEmpty();
    }
}
