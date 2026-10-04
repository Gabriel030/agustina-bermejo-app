package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
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
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class ProgramCohortBillingTest extends AbstractIntegrationTest {
    @Autowired
    private ProgramEnrollmentBillingScheduler billingScheduler;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceFactory invoiceFactory;

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
    private ProgramCohortRepository programCohortRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase createPaymentPlanOptionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase createPaymentPlanOptionForProgramUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase selectPaymentPlanForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase selectPaymentPlanForProgramUseCase;

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPersonBase(String dni) {
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

    private void authenticateAsPerson(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Institución de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
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

    private Term persistTerm(int year) {
        Term term = new Term();
        term.setYear(year);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(java.time.LocalDateTime.of(year, 1, 1, 0, 0));
        term.setEndDate(java.time.LocalDateTime.of(year + 1, 12, 31, 23, 59));
        return termRepository.save(term);
    }

    private ProgramYearBillingTemplate persistTemplate(Program program, Term term, int recurringFeeInvoicesCount, Person createdBy) {
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(recurringFeeInvoicesCount);
        template.setCreatedBy(createdBy);
        return templateRepository.save(template);
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

    private void persistPriceRevision(Program program, FeeType feeType, BigDecimal amount, LocalDate effectiveFrom, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setFeeType(feeType);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(ars(amount));
        revision.setEffectiveFrom(effectiveFrom);
        revision.setCreatedBy(createdBy);
        priceRevisionRepository.save(revision);
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

    @Test
    void studentEnrollingEarlyInTheFirstCohortOfTheNextYearDoesNotReceiveEarlierInstallments() {
        Person director = persistPersonBase("DIR136" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera anticipada " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.of(2025, 8, 1), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100001");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        billingScheduler.generateEnrollmentFee(enrollment);

        var invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(invoices.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void secondCohortEnrollmentFeeIsAutomaticallyFiftyPercentOfTheFirst() {
        Person director = persistPersonBase("DIR137" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera segunda cohorte " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohortSecond = persistCohort(template, CohortIntake.SECOND,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100002");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohortSecond);

        billingScheduler.generateEnrollmentFee(enrollment);

        var invoices = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getBaseAmount()).isEqualTo(ars(50000));
        assertThat(invoice.getAmountDue()).isEqualTo(ars(25000));
        assertThat(invoice.getAdjustments()).hasSize(1);
        assertThat(invoice.getAdjustments().get(0).getAppliedBy()).isNull();
    }

    @Test
    void enrollingOutsideTheCohortsAdmissionWindowIsRejected() {
        Person director = persistPersonBase("DIR138" + System.nanoTime());
        Program program = persistProgram("Carrera ventana cerrada " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), LocalDate.now(), director);

        assertThatThrownBy(() -> programEnrollmentService.validateAdmissionWindow(cohort))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);
    }

    @Test
    void twoCohortsOfTheSameYearHaveIndependentWindowsAndCalendars() {
        Person director = persistPersonBase("DIR139" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera dos cohortes " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort first = persistCohort(template, CohortIntake.FIRST,
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1), director);
        ProgramCohort second = persistCohort(template, CohortIntake.SECOND,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(40000), LocalDate.of(2020, 1, 1), director);

        Student studentA = persistStudent("81100003");
        Student studentB = persistStudent("81100004");
        ProgramEnrollment enrollmentA = persistProgramEnrollment(studentA, program, first);
        ProgramEnrollment enrollmentB = persistProgramEnrollment(studentB, program, second);

        billingScheduler.generateEnrollmentFee(enrollmentA);
        billingScheduler.generateEnrollmentFee(enrollmentB);

        var invoicesA = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollmentA.getId());
        var invoicesB = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollmentB.getId());

        assertThat(invoicesA.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(invoicesA.get(0).getAdjustments()).isEmpty();

        assertThat(invoicesB.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(invoicesB.get(0).getAdjustments()).hasSize(1);
    }

    @Test
    void midYearPriceAdjustmentDoesNotAffectAlreadyGeneratedInstallments() {
        Person director = persistPersonBase("DIR16" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera ajuste de precio " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 3, 1), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100005");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        Invoice invoiceVieja = invoiceFactory.createForProgram(enrollment, null, FeeType.RECURRING_FEE, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 1));
        assertThat(invoiceVieja.getBaseAmount()).isEqualTo(ars(10000));

        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(15000), LocalDate.of(2026, 6, 1), director);

        Invoice invoiceNueva = invoiceFactory.createForProgram(enrollment, null, FeeType.RECURRING_FEE, YearMonth.of(2026, 7), LocalDate.of(2026, 7, 1));
        assertThat(invoiceNueva.getBaseAmount()).isEqualTo(ars(15000));

        Invoice recargada = invoiceRepository.findById(invoiceVieja.getId()).orElseThrow();
        assertThat(recargada.getBaseAmount()).isEqualTo(ars(10000));
    }

    @Test
    void scopeMonthsGreaterThanWhatRemainsOfTheYearIsRejected() {
        Person director = persistPersonBase("DIR37" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera scope mayor " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), LocalDate.now(), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100006");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        PaymentPlanOption option = createPaymentPlanOptionForProgramUseCase.create(program.getId(), request);

        assertThatThrownBy(() -> selectPaymentPlanForProgramUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 12)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);
    }

    @Test
    void oneFullUpfrontYearAndTheNextMonthlyWithoutCarryover() {
        Person director = persistPersonBase("DIR39" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera upfront luego mensual " + System.nanoTime());
        YearMonth mesInicial = YearMonth.now();
        Term term = persistTerm(2026);
        term.setStartDate(mesInicial.atDay(1).atStartOfDay());
        term.setEndDate(mesInicial.plusMonths(9).atEndOfMonth().atTime(23, 59));
        termRepository.save(term);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), LocalDate.now(), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100007");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        CreatePaymentPlanOptionRequest upfrontRequest = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        PaymentPlanOption upfrontOption = createPaymentPlanOptionForProgramUseCase.create(program.getId(), upfrontRequest);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 10));

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial);
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(10);

        Term term2 = persistTerm(2027);
        term2.setStartDate(mesInicial.plusMonths(10).atDay(1).atStartOfDay());
        term2.setEndDate(mesInicial.plusMonths(19).atEndOfMonth().atTime(23, 59));
        termRepository.save(term2);
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);
        programEnrollmentService.advanceAcademicYear(enrollment.getId(), template2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();

        CreatePaymentPlanOptionRequest monthlyRequest = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption monthlyOption = createPaymentPlanOptionForProgramUseCase.create(program.getId(), monthlyRequest);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial.plusMonths(10));

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(11);
    }

    @Test
    void switchingFromMonthlyToUpfrontMidYearAlsoWorks() {
        Person director = persistPersonBase("DIR40" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera mensual a upfront " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), LocalDate.now(), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100008");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        CreatePaymentPlanOptionRequest monthlyRequest = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption monthlyOption = createPaymentPlanOptionForProgramUseCase.create(program.getId(), monthlyRequest);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 3; i++) {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial.plusMonths(i));
        }

        CreatePaymentPlanOptionRequest upfrontRequest = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        PaymentPlanOption upfrontOption = createPaymentPlanOptionForProgramUseCase.create(program.getId(), upfrontRequest);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 7));
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(7);
    }

    @Test
    void studentWithSimultaneousProgramAndCourseKeepsIndependentBillingStates() {
        Person director = persistPersonBase("DIR21" + System.nanoTime());
        authenticateAsPerson(director);

        Program program = persistProgram("Carrera simultánea " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), LocalDate.now(), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100009");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program, cohort);

        CreatePaymentPlanOptionRequest programMonthly = new CreatePaymentPlanOptionRequest(
                "Mensual carrera", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption programOption = createPaymentPlanOptionForProgramUseCase.create(program.getId(), programMonthly);
        selectPaymentPlanForProgramUseCase.select(programEnrollment.getId(), new SelectPaymentPlanRequest(programOption.getId(), null));

        Term courseTerm = persistTerm(2026);
        Course course = new Course();
        course.setName("Curso simultáneo " + System.nanoTime());
        course.setTerm(courseTerm);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(3);
        course = courseRepository.save(course);

        PriceRevision courseRevision = new PriceRevision();
        courseRevision.setCourse(course);
        courseRevision.setFeeType(FeeType.RECURRING_FEE);
        courseRevision.setDescription("Precio de prueba");
        courseRevision.setMonthlyAmount(ars(5000));
        courseRevision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        courseRevision.setCreatedBy(director);
        priceRevisionRepository.save(courseRevision);

        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.ENROLLED);
        courseEnrollment.setEnrollmentDate(java.time.LocalDateTime.now());
        courseEnrollment = courseEnrollmentRepository.save(courseEnrollment);

        CreatePaymentPlanOptionRequest courseMonthly = new CreatePaymentPlanOptionRequest(
                "Mensual curso", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption courseOption = createPaymentPlanOptionForCourseUseCase.create(course.getId(), courseMonthly);
        selectPaymentPlanForCourseUseCase.select(courseEnrollment.getId(), new SelectPaymentPlanRequest(courseOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        invoiceGenerationService.generateNextInstallmentForProgram(programEnrollment, mesInicial);
        invoiceGenerationService.generateNextInstallmentForCourse(courseEnrollment, mesInicial);

        var invoicesCarrera = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId());
        var invoicesCurso = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId());

        assertThat(invoicesCarrera).extracting("feeType").containsExactlyInAnyOrder(FeeType.ENROLLMENT_FEE, FeeType.RECURRING_FEE);
        assertThat(invoicesCurso).extracting("feeType").containsExactly(FeeType.RECURRING_FEE);
        assertThat(invoicesCurso.get(0).getBaseAmount()).isEqualTo(ars(5000));
        assertThat(invoicesCarrera).noneMatch(i -> i.getCourseEnrollment() != null);
        assertThat(invoicesCurso).noneMatch(i -> i.getProgramEnrollment() != null);
    }

    @Autowired
    private com.red_escuela.backend.academicstructure.repository.CourseRepository courseRepository;

    @Autowired
    private com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository courseEnrollmentRepository;
}
