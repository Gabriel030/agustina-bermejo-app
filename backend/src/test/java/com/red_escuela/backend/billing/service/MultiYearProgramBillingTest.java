package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.exception.AnnualUpfrontNotAvailableException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class MultiYearProgramBillingTest extends AbstractIntegrationTest {
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase createPaymentPlanOptionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase createPaymentPlanOptionForProgramUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase selectPaymentPlanForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase selectPaymentPlanForProgramUseCase;

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
    private TermRepository termRepository;

    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;
    @Autowired
    private com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository configurationVersionRepository;

    @Autowired
    private ProgramCohortRepository programCohortRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String dni) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Institución de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Program persistProgram(String nombre) {
        Program program = new Program();
        program.setName(nombre);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private Term persistTerm(int label, YearMonth first, YearMonth last) {
        Term term = new Term();
        term.setYear(label);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(first.atDay(1).atStartOfDay());
        term.setEndDate(last.atEndOfMonth().atTime(23, 59));
        return termRepository.save(term);
    }

    private ProgramYearBillingTemplate persistTemplate(Program program, Term term, int recurringFeeInvoicesCount, Person createdBy) {
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, recurringFeeInvoicesCount);
        template.setCreatedBy(createdBy);
        return com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(configurationVersionRepository, templateRepository.save(template));
    }

    private ProgramCohort persistCohort(ProgramYearBillingTemplate template, CohortIntake intake,
                                         LocalDate admissionStart, LocalDate admissionEnd, LocalDate cursadaStart, Person createdBy) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(intake);
        cohort.setAdmissionStart(admissionStart);
        cohort.setAdmissionEnd(admissionEnd);
        cohort.setCursadaStart(cursadaStart);
        cohort.setCreatedBy(createdBy);
        return programCohortRepository.save(cohort);
    }

    private void persistPriceRevision(Program program, FeeType feeType, BigDecimal amount, Person createdBy) {
        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, feeType, "Precio de prueba", ars(amount), LocalDate.of(2020, 1, 1), createdBy.getId());
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramCohort cohort) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(java.time.LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    private long tuitionInvoiceCount(Long programEnrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId).stream()
                .filter(i -> i.getFeeType() == FeeType.MONTHLY_INSTALLMENT)
                .count();
    }

    private PaymentPlanOption monthlyPlan(Program program, Person director) {
        authenticateAs(director);
        return createPaymentPlanOptionForProgramUseCase.create(program.getId(),
                new CreatePaymentPlanOptionRequest("Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null));
    }

    private PaymentPlanOption upfrontPlan(Program program, Person director) {
        authenticateAs(director);
        return createPaymentPlanOptionForProgramUseCase.create(program.getId(),
                new CreatePaymentPlanOptionRequest("Adelantado", PaymentPlanCadence.ANNUAL_UPFRONT, null, true,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(60)));
    }

    @Test
    void twoYearCareerGeneratesEachYearsInvoicesSeparately() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIR2Y" + System.nanoTime());
        Program program = persistProgram("Carrera 2 años " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(10000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term termAño1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate templateAño1 = persistTemplate(program, termAño1, 10, director);
        ProgramCohort cohortAño1 = persistCohort(templateAño1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term termAño2 = persistTerm(2, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate templateAño2 = persistTemplate(program, termAño2, 10, director);

        Student student = persistStudent("83000001");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohortAño1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(10);

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), templateAño2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(20);

        ProgramEnrollment recargado = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(recargado.getProgramCohort().getId()).isEqualTo(cohortAño1.getId());
        assertThat(recargado.getCurrentBillingTemplate().getId()).isEqualTo(templateAño2.getId());
    }

    @Test
    void twoAndHalfYearCareerLastYearHasFewerInvoices() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIR25Y" + System.nanoTime());
        Program program = persistProgram("Carrera 2.5 años " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(8000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);

        Term term3 = persistTerm(3, base.plusMonths(20), base.plusMonths(24));
        ProgramYearBillingTemplate template3Corto = persistTemplate(program, term3, 5, director);

        Student student = persistStudent("83000002");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(i));
        }

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), template2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(10 + i));
        }

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), template3Corto.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(20 + i));
        }

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(25);
    }

    @Test
    void studentRepeatingAYearDoesNotAccumulateAgainstCareerTotal() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRREP" + System.nanoTime());
        Program program = persistProgram("Carrera con repitente " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(9000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now().plusMonths(10), director);

        Term termRepite = persistTerm(2, base.plusMonths(20), base.plusMonths(29));
        ProgramYearBillingTemplate templateRepite = persistTemplate(program, termRepite, 10, director);

        Student student = persistStudent("83000003");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(10);

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), templateRepite.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        PaymentPlanOption mensualRepite = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensualRepite.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(20 + i));
        }

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(20);
    }

    @Test
    void studentAlternatesPaymentMethodsWithinAndAcrossYearsNoCarryover() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRINT" + System.nanoTime());
        Program program = persistProgram("Carrera intercalada " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(7000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);

        Student student = persistStudent("83000004");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 4; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(4);

        PaymentPlanOption adelantado = upfrontPlan(program, director);
        authenticateAs(director);
        Long enrollmentId = enrollment.getId();
        assertThatThrownBy(() -> selectPaymentPlanForProgramUseCase.select(enrollmentId, new SelectPaymentPlanRequest(adelantado.getId(), 6)))
                .isInstanceOf(AnnualUpfrontNotAvailableException.class);
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(4);

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), template2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        PaymentPlanOption mensualAño2 = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensualAño2.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(14);
    }

    @Test
    void annualUpfrontIsRejectedEvenAfterACompleteYear() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRTOPE" + System.nanoTime());
        Program program = persistProgram("Carrera con tope por año " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(6000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(18));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 8, director);

        Student student = persistStudent("83000005");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(i));
        }

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), template2.getId());
        PaymentPlanOption adelantado = upfrontPlan(program, director);
        authenticateAs(director);

        assertThatThrownBy(() -> selectPaymentPlanForProgramUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(adelantado.getId(), 9)))
                .isInstanceOf(AnnualUpfrontNotAvailableException.class);
    }

    @Test
    void monthlyGenerationBeyondTemplateCapIsSilentNoOp() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRTOPEMENS" + System.nanoTime());
        Program program = persistProgram("Carrera con tope mensual " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(5000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template = persistTemplate(program, term, 4, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Student student = persistStudent("83000006");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        for (int i = 0; i < 6; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(i));
        }

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(4);
    }

    @Test
    void monthlyGenerationOutsideCohortWindowIsSilentNoOp() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRVENTMENS" + System.nanoTime());
        Program program = persistProgram("Carrera con ventana mensual " + System.nanoTime());
        persistPriceRevision(program, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(5000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term = persistTerm(1, base, base.plusMonths(4));
        ProgramYearBillingTemplate template = persistTemplate(program, term, 3, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Student student = persistStudent("83000007");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base);
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, base.plusMonths(10));

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(1);
    }
}
