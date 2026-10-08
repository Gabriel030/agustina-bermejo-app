package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.LatePaymentSettingsRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class ScholarshipServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForCourseUseCase grantScholarshipForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForProgramUseCase grantScholarshipForProgramUseCase;

    @Autowired
    private BillingTestTrigger invoiceGenerationService;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase selectPaymentPlanForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase selectPaymentPlanForProgramUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase createPaymentPlanOptionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase createPaymentPlanOptionForProgramUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceOverdueSweepService invoiceOverdueSweepService;

    @Autowired
    private LatePaymentSettingsRepository latePaymentSettingsRepository;

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
    private CourseEnrollmentService courseEnrollmentService;

    @Autowired
    private com.red_escuela.backend.billing.repository.ScholarshipRepository scholarshipRepository;

    @Autowired
    private com.red_escuela.backend.billing.repository.CreditNoteRepository creditNoteRepository;

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

    private Course persistCourse(Person director, BigDecimal precio) {
        return persistCourse(director, precio, null);
    }

    private Course persistCourse(Person director, BigDecimal precio, Integer durationMonths) {
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
        course.setDurationMonths(durationMonths);
        course = courseRepository.save(course);

        PriceRevision priceRevision = new PriceRevision();
        priceRevision.setCourse(course);
        priceRevision.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        priceRevision.setDescription("Cuota mensual de prueba");
        priceRevision.setMonthlyAmount(ars(precio));
        priceRevision.setEffectiveFrom(LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(director);
        priceRevisionRepository.save(priceRevision);

        return course;
    }

    private void authenticateAs(Person person) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(person.getId().toString(), null));
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void seleccionarPlanMensual(CourseEnrollment enrollment, Person director) {
        authenticateAs(director);
        var option = createPaymentPlanOptionForCourseUseCase.create(enrollment.getCourse().getId(),
                new com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest(
                        "Mensual", com.red_escuela.backend.billing.entity.PaymentPlanCadence.MONTHLY, null, false, null, null));
        selectPaymentPlanForCourseUseCase.select(enrollment.getId(),
                new com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest(option.getId(), null));
    }

    @Test
    void hundredPercentScholarshipMakesTheInvoiceBornDirectlyWaived() {
        Person director = persistPerson("director.scholarship1." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000001", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.ONE)), "Beca completa", 3));

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        assertThatInvoice(invoice).isWaived();
        assertThat(invoice.getAmounts().total().value()).isEqualTo(ars(0));
    }

    @Test
    void partialScholarshipCorrectlyDiscountsFromEachNewInstallment() {
        Person director = persistPerson("director.scholarship2." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000002", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.30))), "Beca 30%", 3));

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        assertThatInvoice(invoice).isPending();
        assertThat(invoice.getAmounts().total().value()).isEqualTo(ars(7000));
    }

    @Test
    void fixedAmountScholarshipDoesNotAdjustItselfForAPriceIncrease() {
        Person director = persistPerson("director.scholarship3." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000003", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.NOMINAL, ars(5000), null), "Beca fija $5.000", 3));

        PriceRevision aumento = new PriceRevision();
        aumento.setCourse(course);
        aumento.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        aumento.setDescription("Ajuste inflacionario");
        aumento.setMonthlyAmount(ars(20000));
        aumento.setEffectiveFrom(YearMonth.now().atDay(1));
        aumento.setCreatedBy(director);
        priceRevisionRepository.save(aumento);

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        assertThat(invoice.getAmounts().linesTotal().value()).isEqualTo(ars(20000));
        assertThat(invoice.getAmounts().total().value()).isEqualTo(ars(15000));
    }

    @Test
    void aManualAdjustmentThatSettlesTheTotalLeavesTheInvoiceWaived() {
        Person director = persistPerson("director.scholarship4." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(5000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000004", course);
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now()).dueOn(LocalDate.now()).withBaseAmount(ars(5000)).build();
        invoice = invoiceRepository.save(invoice);

        invoice.applyAdjustment(com.red_escuela.backend.billing.entity.DiscountType.DISCOUNT,
                new MonetaryAdjustment.Proportional(Percentage.ofFraction(BigDecimal.ONE)), "Condonación total", director);
        invoiceRepository.save(invoice);

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isWaived();
    }

    @Test
    void aWaivedInstallmentNeverReceivesASurchargeEvenIfItBecomesOverdue() {
        Person director = persistPerson("director.scholarship5." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(5000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000005", course);

        LatePaymentSettings policy = LatePaymentSettings.builder()
                .gracePeriodDays(10)
                .surchargeRate(Percentage.ofFraction(BigDecimal.valueOf(0.10)))
                .suspensionAfterDays(30)
                .suspensionAfterInvoiceCount(2)
                .effectiveFrom(LocalDate.now().minusYears(1))
                .createdBy(director)
                .build();
        latePaymentSettingsRepository.save(policy);

        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().minusMonths(1)).dueOn(LocalDate.now().minusDays(20)).withBaseAmount(ars(5000)).build();
        BillingTestFixtures.waiveInvoice(invoice);
        invoice = invoiceRepository.save(invoice);

        invoiceOverdueSweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
        assertThatInvoice(reloaded).isWaived();
        assertThat(com.red_escuela.backend.billing.support.InvoiceViews.discountsOf(reloaded)).isEmpty();
    }

    @Test
    void grantingAScholarshipWithMoreInstallmentsThanTheCourseDurationIsRejected() {
        Person director = persistPerson("director.scholarship6." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000), 3);
        CourseEnrollment enrollment = persistCourseEnrollment("88000006", course);
        seleccionarPlanMensual(enrollment, director);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                        new CreateScholarshipRequest(new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.20))), "Beca 20%", 5)))
                .isInstanceOf(com.red_escuela.backend.billing.exception.ScholarshipExceedsCohortSpanException.class);
    }

    @Test
    void aSecondGrantWhileAScholarshipIsActiveIsRejectedByTheDatabaseConstraint() {
        Person director = persistPerson("director.scholarship8." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000008", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                new CreateScholarshipRequest(new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.10))), "Beca 10%", 3));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                        new CreateScholarshipRequest(new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.20))), "Beca 20%", 2)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void withdrawingCancelsTheScholarshipsPendingInstallments() {
        Person director = persistPerson("director.scholarship7." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000007", course);
        seleccionarPlanMensual(enrollment, director);

        var scholarship = grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                new CreateScholarshipRequest(new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.10))), "Beca 10%", 3));

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());
        courseEnrollmentService.withdraw(enrollment.getId());

        var reloaded = scholarshipRepository.findByIdWithInstallments(scholarship.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(com.red_escuela.backend.billing.entity.ScholarshipStatus.INACTIVE);
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.CANCELLED)
                .hasSize(2);
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.APPLIED)
                .hasSize(1);
    }

    @Test
    @Tag("D-078")
    void grantingAScholarshipWithTheCurrentMonthsInstallmentAlreadyPaidGeneratesARetroactiveCreditNote() {
        Person director = persistPerson("director.scholarship9." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000009", course);
        seleccionarPlanMensual(enrollment, director);

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());
        Invoice cuotaDelMes = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0);
        BillingTestFixtures.payInvoiceInFull(cuotaDelMes);
        invoiceRepository.save(cuotaDelMes);

        var scholarship = grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null, Percentage.ofFraction(BigDecimal.valueOf(0.20))), "Beca 20% tardía", 3));

        var creditNote = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId()).get(0);
        assertThat(creditNote.getReasonType()).isEqualTo(com.red_escuela.backend.billing.entity.CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP);
        assertThat(creditNote.getAmount()).isEqualTo(ars(2000));

        var reloaded = scholarshipRepository.findByIdWithInstallments(scholarship.getId()).orElseThrow();
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.APPLIED)
                .hasSize(1);
    }

    @Test
    @Tag("D-035")
    void t12CoursePriceIncreasedOnDayThreeAppliesToTheInvoiceEmittedOnDayFourAndNotToTheOneEmittedOnDayOne() {
        Person director = persistPerson("director.t12." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment early = persistCourseEnrollment("88000091", course);
        CourseEnrollment late = persistCourseEnrollment("88000092", course);
        com.red_escuela.backend.shared.time.MutableClock.INSTANCE.setDate(LocalDate.of(2026, 6, 1));
        seleccionarPlanMensual(early, director);
        seleccionarPlanMensual(late, director);

        invoiceGenerationService.generateNextInstallmentForCourse(early, YearMonth.of(2026, 6));

        PriceRevision increase = new PriceRevision();
        increase.setCourse(course);
        increase.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        increase.setDescription("Aumento");
        increase.setMonthlyAmount(ars(BigDecimal.valueOf(12000)));
        increase.setEffectiveFrom(LocalDate.of(2026, 6, 3));
        increase.setCreatedBy(director);
        priceRevisionRepository.save(increase);
        com.red_escuela.backend.shared.time.MutableClock.INSTANCE.setDate(LocalDate.of(2026, 6, 4));
        invoiceGenerationService.generateNextInstallmentForCourse(late, YearMonth.of(2026, 6));

        Invoice first = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(early.getId()).get(0);
        Invoice second = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(late.getId()).get(0);
        assertThat(first.getAmounts().linesTotal().value()).isEqualTo(ars(10000));
        assertThat(first.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(second.getAmounts().linesTotal().value()).isEqualTo(ars(12000));
        assertThat(second.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(early.getId()).get(0).getAmounts().linesTotal().value()).isEqualTo(ars(10000));
    }
}
