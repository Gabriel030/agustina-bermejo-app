package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.CreatePromotionBenefitRequest;
import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentValueRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.entity.PromotionStatus;
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
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.repository.PromotionRepository;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;

@SpringBootTest
@ActiveProfiles("test")
class PromotionServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.promotion.CreatePromotionForCourseUseCase createPromotionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.promotion.CreatePromotionForProgramUseCase createPromotionForProgramUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.promotion.ClosePromotionUseCase closePromotionUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.promotion.FindActivePromotionsUseCase findActivePromotionsUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.promotion.FindLostPromotionBenefitsUseCase findLostPromotionBenefitsUseCase;

    @Autowired
    private PromotionRepository promotionRepository;

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

    @Autowired
    private InvoiceQueryService invoiceQueryService;

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

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;

    @Autowired
    private ProgramCohortRepository programCohortRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(username);
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

    private Course persistCourse(Person director, BigDecimal precio) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 12, 31, 23, 59));
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        PriceRevision priceRevision = new PriceRevision();
        priceRevision.setCourse(course);
        priceRevision.setFeeType(FeeType.RECURRING_FEE);
        priceRevision.setDescription("Cuota mensual de prueba");
        priceRevision.setMonthlyAmount(precio);
        priceRevision.setEffectiveFrom(LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(director);
        priceRevisionRepository.save(priceRevision);

        return course;
    }

    private CourseEnrollment persistCourseEnrollment(String dni, Course course, LocalDateTime enrollmentDate) {
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
        enrollment.setEnrollmentDate(enrollmentDate);
        return courseEnrollmentRepository.save(enrollment);
    }

    private PaymentPlanSelection seleccionarPlanMensualCurso(CourseEnrollment enrollment, Person director) {
        authenticateAs(director);
        PaymentPlanOption option = createPaymentPlanOptionForCourseUseCase.create(enrollment.getCourse().getId(),
                new CreatePaymentPlanOptionRequest("Mensual " + System.nanoTime(), PaymentPlanCadence.MONTHLY, null, false, null, null));
        return selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), null));
    }

    private ProgramEnrollment persistProgramEnrollment(String dni, LocalDateTime enrollmentDate, Person director) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(1);
        program = programRepository.save(program);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 12, 31, 23, 59));
        term = termRepository.save(term);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(11);
        template.setCreatedBy(director);
        template = templateRepository.save(template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now().minusMonths(2));
        cohort.setAdmissionEnd(LocalDate.now().plusMonths(2));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(director);
        cohort = programCohortRepository.save(cohort);

        PriceRevision recurringFee = new PriceRevision();
        recurringFee.setProgram(program);
        recurringFee.setFeeType(FeeType.RECURRING_FEE);
        recurringFee.setDescription("Cuota mensual de prueba");
        recurringFee.setMonthlyAmount(BigDecimal.valueOf(10000));
        recurringFee.setEffectiveFrom(LocalDate.now().minusYears(1));
        recurringFee.setCreatedBy(director);
        priceRevisionRepository.save(recurringFee);

        PriceRevision enrollmentFee = new PriceRevision();
        enrollmentFee.setProgram(program);
        enrollmentFee.setFeeType(FeeType.ENROLLMENT_FEE);
        enrollmentFee.setDescription("Matrícula de prueba");
        enrollmentFee.setMonthlyAmount(BigDecimal.valueOf(20000));
        enrollmentFee.setEffectiveFrom(LocalDate.now().minusYears(1));
        enrollmentFee.setCreatedBy(director);
        priceRevisionRepository.save(enrollmentFee);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(enrollmentDate);
        return programEnrollmentRepository.save(enrollment);
    }

    private PaymentPlanSelection seleccionarPlanMensualCarrera(ProgramEnrollment enrollment, Person director) {
        authenticateAs(director);
        PaymentPlanOption option = createPaymentPlanOptionForProgramUseCase.create(enrollment.getProgram().getId(),
                new CreatePaymentPlanOptionRequest("Mensual " + System.nanoTime(), PaymentPlanCadence.MONTHLY, null, false, null, null));
        return selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), null));
    }

    private Invoice invoiceConAjustesDe(CourseEnrollment enrollment, int index) {
        List<Invoice> invoices = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId());
        return invoiceRepository.findByIdWithAdjustments(invoices.get(index).getId()).orElseThrow();
    }

    @Test
    void studentWhoChoosesTheirFirstPlanWithinThePromotionWindowQualifiesAutomatically() {
        Person director = persistPerson("director.promo108." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000108", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Invoice invoice = invoiceConAjustesDe(enrollment, 0);
        assertThat(invoice.getAdjustments()).anyMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(9000));
    }

    @Test
    void studentWhoEnrollsOutsideTheWindowDoesNotQualify() {
        Person director = persistPerson("director.promo109." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(10), LocalDate.now().minusDays(3),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000109", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Invoice invoice = invoiceConAjustesDe(enrollment, 0);
        assertThat(invoice.getAdjustments()).noneMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
    }

    @Test
    void theFirstTwoInstallmentsCarryTheDiscountAndTheThirdDoesNot() {
        Person director = persistPerson("director.promo110." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 2))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000110", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(enrollment, director);
        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 3; i++) {
            invoiceGenerationService.generateNextInstallmentForCourse(enrollment, mesInicial.plusMonths(i));
        }

        assertThat(invoiceConAjustesDe(enrollment, 0).getAdjustments()).anyMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
        assertThat(invoiceConAjustesDe(enrollment, 1).getAdjustments()).anyMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
        assertThat(invoiceConAjustesDe(enrollment, 2).getAdjustments()).noneMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
    }

    @Test
    void theBenefitWithoutAQuantityLimitAppliesToAllGeneratedInstallments() {
        Person director = persistPerson("director.promo111." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), null))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000111", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(enrollment, director);
        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 4; i++) {
            invoiceGenerationService.generateNextInstallmentForCourse(enrollment, mesInicial.plusMonths(i));
        }

        for (int i = 0; i < 4; i++) {
            assertThat(invoiceConAjustesDe(enrollment, i).getAdjustments()).anyMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
        }
    }

    @Test
    void closingAPromotionPreventsNewQualificationsWithoutAffectingThoseAlreadyGranted() {
        Person director = persistPerson("director.promo113." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        var promotion = createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        CourseEnrollment yaCalificado = persistCourseEnrollment("89000113a", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(yaCalificado, director);

        authenticateAs(director);
        closePromotionUseCase.close(promotion.getId());
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getStatus()).isEqualTo(PromotionStatus.CLOSED);

        CourseEnrollment despuesDelCierre = persistCourseEnrollment("89000113b", course, LocalDateTime.now());
        seleccionarPlanMensualCurso(despuesDelCierre, director);

        invoiceGenerationService.generateNextInstallmentForCourse(yaCalificado, YearMonth.now());
        invoiceGenerationService.generateNextInstallmentForCourse(despuesDelCierre, YearMonth.now());

        assertThat(invoiceConAjustesDe(yaCalificado, 0).getAdjustments()).anyMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
        assertThat(invoiceConAjustesDe(despuesDelCierre, 0).getAdjustments()).noneMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
    }

    @Test
    void twoActivePromotionsForTheSameCourseDoNotCombine() {
        Person director = persistPerson("director.promo114." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Promo A", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Promo B", LocalDate.now().minusDays(1), LocalDate.now().plusDays(6),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.20)), 3))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000114", course, LocalDateTime.now());
        PaymentPlanSelection selection = seleccionarPlanMensualCurso(enrollment, director);

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());
        Invoice invoice = invoiceConAjustesDe(enrollment, 0);
        long cantidadDeAjustesPromocion = invoice.getAdjustments().stream()
                .filter(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION).count();
        assertThat(cantidadDeAjustesPromocion).isEqualTo(1);
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(9000));
    }

    @Test
    void changingPlanImmediatelyCutsOffTheBenefit() {
        Person director = persistPerson("director.promo115." + System.nanoTime());
        ProgramEnrollment enrollment = persistProgramEnrollment("89000115", LocalDateTime.now(), director);
        authenticateAs(director);
        createPromotionForProgramUseCase.create(enrollment.getProgram().getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        seleccionarPlanMensualCarrera(enrollment, director);
        YearMonth mesInicial = YearMonth.now();
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial);
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial.plusMonths(1));

        seleccionarPlanMensualCarrera(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, mesInicial.plusMonths(2));

        List<Invoice> recurringInvoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE).toList();
        assertThat(recurringInvoices).hasSize(3);
        Invoice tercera = invoiceRepository.findByIdWithAdjustments(recurringInvoices.get(2).getId()).orElseThrow();
        assertThat(tercera.getAdjustments()).noneMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
    }

    @Test
    void goingBackToTheOriginalPlanDoesNotRecoverTheLostBenefit() {
        Person director = persistPerson("director.promo116." + System.nanoTime());
        ProgramEnrollment enrollment = persistProgramEnrollment("89000116", LocalDateTime.now(), director);
        authenticateAs(director);
        createPromotionForProgramUseCase.create(enrollment.getProgram().getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        seleccionarPlanMensualCarrera(enrollment, director);
        seleccionarPlanMensualCarrera(enrollment, director);
        seleccionarPlanMensualCarrera(enrollment, director);

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());
        List<Invoice> recurringInvoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE).toList();
        Invoice invoice = invoiceRepository.findByIdWithAdjustments(recurringInvoices.get(0).getId()).orElseThrow();
        assertThat(invoice.getAdjustments()).noneMatch(a -> a.getType() == com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.PROMOTION);
    }

    @Test
    void benefitLostDueToPlanChangeAppearsInTheReport() {
        Person director = persistPerson("director.promo122." + System.nanoTime());
        ProgramEnrollment enrollment = persistProgramEnrollment("89000122", LocalDateTime.now(), director);
        authenticateAs(director);
        createPromotionForProgramUseCase.create(enrollment.getProgram().getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        seleccionarPlanMensualCarrera(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());
        seleccionarPlanMensualCarrera(enrollment, director);

        List<com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse> perdidos = findLostPromotionBenefitsUseCase.find();
        assertThat(perdidos).anyMatch(p -> p.studentName().equals("Persona 89000122")
                && p.remaining() == 2 && p.used() == 1 && p.feeType().equals("RECURRING_FEE"));
    }

    @Test
    void thePriceBreakdownShowsThePromotionNameAndThePlanThatEnabledIt() {
        Person director = persistPerson("director.promo117." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);
        createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        CourseEnrollment enrollment = persistCourseEnrollment("89000117", course, LocalDateTime.now());
        PaymentPlanSelection selection = seleccionarPlanMensualCurso(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Long invoiceId = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId();
        var breakdown = invoiceQueryService.priceBreakdown(invoiceId);
        var lineaPromocion = breakdown.adjustments().stream()
                .filter(l -> l.type().equals("PROMOTION"))
                .findFirst().orElseThrow();
        assertThat(lineaPromocion.humanLabel()).contains("Semana del estudiante");
        assertThat(lineaPromocion.humanLabel()).contains(selection.getPaymentPlanOption().getDescription());
    }

    @Test
    void aStillOpenPlanSelectionNeverAppearsAsALostBenefit() {
        Person director = persistPerson("director.promo123." + System.nanoTime());
        ProgramEnrollment enrollment = persistProgramEnrollment("89000123", LocalDateTime.now(), director);
        authenticateAs(director);
        createPromotionForProgramUseCase.create(enrollment.getProgram().getId(), new CreatePromotionRequest(
                "Semana del estudiante", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3))));

        seleccionarPlanMensualCarrera(enrollment, director);
        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, YearMonth.now());

        List<com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse> perdidos = findLostPromotionBenefitsUseCase.find();
        assertThat(perdidos).noneMatch(p -> p.studentName().equals("Persona 89000123"));
    }

    @Test
    void twoBenefitsWithTheSameFeeTypeInTheSamePromotionAreRejected() {
        Person director = persistPerson("director.promo.duplicado." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                        "Promo con benefits duplicados", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5),
                        List.of(
                                new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3),
                                new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.20)), 1)))))
                .isInstanceOf(com.red_escuela.backend.billing.exception.DuplicatePromotionBenefitFeeTypeException.class);
    }

    @Test
    void anInvertedEligibilityWindowIsRejected() {
        Person director = persistPerson("director.promo.ventana." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        authenticateAs(director);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> createPromotionForCourseUseCase.create(course.getId(), new CreatePromotionRequest(
                        "Promo con fechas invertidas", LocalDate.now().plusDays(5), LocalDate.now().minusDays(2),
                        List.of(new CreatePromotionBenefitRequest(FeeType.RECURRING_FEE, new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), 3)))))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException.class);
    }
}
