package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceGenerationSuspensionTest extends AbstractIntegrationTest {
    @Autowired
    private BillingTestTrigger invoiceGenerationService;

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
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PaymentPlanOptionRepository paymentPlanOptionRepository;

    @Autowired
    private PaymentPlanSelectionRepository paymentPlanSelectionRepository;

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Person person = new Person();
        person.setUsername(dni);
        person.setName("Alumno");
        person.setLastname("Prueba");
        person.setDni(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(addressRepository.save(new Address()));
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private ProgramEnrollment persistProgramEnrollment(String dni, ProgramEnrollmentStatus status, EnrollmentPaymentStatus paymentStatus) {
        Student student = persistStudent(dni);
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setPaymentStatus(paymentStatus);
        return programEnrollmentRepository.save(enrollment);
    }

    private CourseEnrollment persistCourseEnrollment(String dni, EnrollmentStatus status, EnrollmentPaymentStatus paymentStatus) {
        Student student = persistStudent(dni);
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
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setPaymentStatus(paymentStatus);
        return courseEnrollmentRepository.save(enrollment);
    }

    @Test
    @Tag("D-053")
    @Tag("D-066")
    void suspendedDoesNotGenerateNewInstallmentTheFollowingMonth() {
        ProgramEnrollment enrollment = persistProgramEnrollment("99000201", ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED);

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }

    @Test
    void withdrawnForNonpaymentAlsoGeneratesNothing() {
        ProgramEnrollment enrollment = persistProgramEnrollment(
                "99000202", ProgramEnrollmentStatus.WITHDRAWN, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }

    @Test
    void theSameRuleAppliesToAllThreeBillableTypes() {
        CourseEnrollment courseEnrollment = persistCourseEnrollment("99000203", EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED);

        invoiceGenerationService.generateNextInstallmentForCourse(courseEnrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId())).isEmpty();
    }

    @Test
    @Tag("D-066")
    void payingOffOldDebtResumesGenerationWithoutRecoveringSkippedMonths() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000205", EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED);
        Person director = persistStudent("99000206").getPerson();

        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, enrollment.getCourse().getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota mensual de prueba", ars(1000), java.time.LocalDate.now().minusYears(1), director.getId());

        PaymentPlanOption option = new PaymentPlanOption();
        option.setCourse(enrollment.getCourse());
        option.setDescription("Mensual");
        option.setCadence(PaymentPlanCadence.MONTHLY);
        option.setCreatedBy(director);
        option = paymentPlanOptionRepository.save(option);

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setCourseEnrollment(enrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now().minusMonths(3));
        selection.setCreatedBy(director);
        paymentPlanSelectionRepository.save(selection);

        enrollment.setPaymentStatus(EnrollmentPaymentStatus.UP_TO_DATE);
        courseEnrollmentRepository.save(enrollment);

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        List<Invoice> invoices = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getPeriod()).isEqualTo(YearMonth.now());
    }

    @Test
    void manualWithdrawalByAdministrativeImmediatelyStopsGeneration() {
        ProgramEnrollment enrollment = persistProgramEnrollment("99000204", ProgramEnrollmentStatus.WITHDRAWN, EnrollmentPaymentStatus.UP_TO_DATE);

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }
}
