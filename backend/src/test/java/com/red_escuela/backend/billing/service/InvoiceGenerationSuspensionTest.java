package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.3: tests 24-28 — un billable SUSPENDED/WITHDRAWN_FOR_NONPAYMENT (o dado de baja
 * manualmente) no debe seguir generando cuotas nuevas, y al saldar la deuda vieja se retoma la
 * generación sin recuperar retroactivamente los meses salteados.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceGenerationSuspensionTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

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

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Person person = new Person();
        person.setUsername(dni);
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

    private ProgramEnrollment persistProgramEnrollment(String dni, ProgramEnrollmentStatus status, EnrollmentBillingStanding billingStanding) {
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
        enrollment.setBillingStanding(billingStanding);
        return programEnrollmentRepository.save(enrollment);
    }

    private CourseEnrollment persistCourseEnrollment(String dni, EnrollmentStatus status, EnrollmentBillingStanding billingStanding) {
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
        enrollment.setBillingStanding(billingStanding);
        return courseEnrollmentRepository.save(enrollment);
    }

    @Test
    void suspendidoNoGeneraCuotaNuevaElMesSiguiente() {
        ProgramEnrollment enrollment = persistProgramEnrollment("99000201", ProgramEnrollmentStatus.ENROLLED, EnrollmentBillingStanding.SUSPENDED);

        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }

    @Test
    void withdrawnForNonpaymentTampocoGeneraNada() {
        ProgramEnrollment enrollment = persistProgramEnrollment(
                "99000202", ProgramEnrollmentStatus.WITHDRAWN, EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);

        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }

    @Test
    void laMismaReglaAplicaAlosTresTiposDeBillable() {
        CourseEnrollment courseEnrollment = persistCourseEnrollment("99000203", EnrollmentStatus.ENROLLED, EnrollmentBillingStanding.SUSPENDED);

        invoiceGenerationService.generarProximaCuotaParaCurso(courseEnrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId())).isEmpty();
    }

    @Test
    void alSaldarLaDeudaViejaSeRetomaLaGeneracionSinRecuperarLosMesesSalteados() {
        CourseEnrollment enrollment = persistCourseEnrollment("99000205", EnrollmentStatus.ENROLLED, EnrollmentBillingStanding.SUSPENDED);
        Person director = persistStudent("99000206").getPerson();

        PriceRevision priceRevision = new PriceRevision();
        priceRevision.setCourse(enrollment.getCourse());
        priceRevision.setFeeType(FeeType.RECURRING_FEE);
        priceRevision.setDescription("Cuota mensual de prueba");
        priceRevision.setMonthlyAmount(java.math.BigDecimal.valueOf(1000));
        priceRevision.setEffectiveFrom(java.time.LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(director);
        priceRevisionRepository.save(priceRevision);

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

        // Dos meses "salteados" mientras estuvo SUSPENDED: se saldó la deuda vieja y vuelve a
        // UP_TO_DATE, pero esos dos meses nunca deberían generarse retroactivamente.
        enrollment.setBillingStanding(EnrollmentBillingStanding.UP_TO_DATE);
        courseEnrollmentRepository.save(enrollment);

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        List<Invoice> invoices = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getPeriod()).isEqualTo(YearMonth.now());
    }

    @Test
    void bajaManualPorAdministrativoDetieneLaGeneracionDeInmediato() {
        // WITHDRAWN académico pero NO por mora (billingStanding sigue UP_TO_DATE) — el chequeo de
        // status académico en InvoiceGenerationService es la única barrera acá, sin depender de
        // que el sweep de cobranza haya corrido.
        ProgramEnrollment enrollment = persistProgramEnrollment("99000204", ProgramEnrollmentStatus.WITHDRAWN, EnrollmentBillingStanding.UP_TO_DATE);

        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId())).isEmpty();
    }
}
