package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentValueRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;

// Fase 6.4: tests 11, 32/82 (monto fijo no se ajusta solo), 80, 81. NO @Transactional a
// propósito: InvoiceFactory.crearParaCurso usa REQUIRES_NEW (mismo motivo que el resto de los
// tests de facturación de este proyecto).
@SpringBootTest
@ActiveProfiles("test")
class ScholarshipServiceTest extends AbstractIntegrationTest {

    @Autowired
    private com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForCourseUseCase grantScholarshipForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForProgramUseCase grantScholarshipForProgramUseCase;

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

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
    private CollectionsPolicyRepository collectionsPolicyRepository;

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
        priceRevision.setFeeType(FeeType.RECURRING_FEE);
        priceRevision.setDescription("Cuota mensual de prueba");
        priceRevision.setMonthlyAmount(precio);
        priceRevision.setEffectiveFrom(LocalDate.now().minusYears(1));
        priceRevision.setCreatedBy(director);
        priceRevisionRepository.save(priceRevision);

        return course;
    }

    private void autenticarComo(Person person) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(person.getId().toString(), null));
    }

    @org.junit.jupiter.api.AfterEach
    void limpiarContextoDeSeguridad() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void seleccionarPlanMensual(CourseEnrollment enrollment, Person director) {
        autenticarComo(director);
        var option = createPaymentPlanOptionForCourseUseCase.create(enrollment.getCourse().getId(),
                new com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest(
                        "Mensual", com.red_escuela.backend.billing.entity.PaymentPlanCadence.MONTHLY, null, false, null, null));
        selectPaymentPlanForCourseUseCase.select(enrollment.getId(),
                new com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest(option.getId(), null));
    }

    @Test
    void becaDelCienPorCientoHaceNacerLaInvoiceDirectamenteEnWaived() {
        Person director = persistPerson("director.scholarship1." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000001", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentValueRequest(null, BigDecimal.ONE), "Beca completa", 3));

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.WAIVED);
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void becaParcialDescuentaCorrectamenteDeCadaCuotaNueva() {
        Person director = persistPerson("director.scholarship2." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000002", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.30)), "Beca 30%", 3));

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(7000));
    }

    // Test 32/82: una Scholarship(FixedAmount) no se ajusta sola por un PriceRevision nuevo.
    @Test
    void becaDeMontoFijoNoSeAjustaSolaPorUnAumentoDePrecio() {
        Person director = persistPerson("director.scholarship3." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000003", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentValueRequest(BigDecimal.valueOf(5000), null), "Beca fija $5.000", 3));

        // Sube el precio del curso (nuevo PriceRevision).
        PriceRevision aumento = new PriceRevision();
        aumento.setCourse(course);
        aumento.setFeeType(FeeType.RECURRING_FEE);
        aumento.setDescription("Ajuste inflacionario");
        aumento.setMonthlyAmount(BigDecimal.valueOf(20000));
        aumento.setEffectiveFrom(YearMonth.now().atDay(1));
        aumento.setCreatedBy(director);
        priceRevisionRepository.save(aumento);

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        Invoice invoice = invoiceRepository.findByIdWithAdjustments(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0).getId()).orElseThrow();
        // Precio nuevo 20000, descuento sigue siendo $5.000 fijo (no un % actualizado).
        assertThat(invoice.getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    // Test 80: otorgar un ajuste manual del 100% sobre una Invoice PENDING ya existente la deja
    // en WAIVED — no hace falta que nazca así, la transición corre para cualquier ajuste que deje
    // el saldo en cero.
    @Test
    void unAjusteManualQueSaldaElTotalDejaLaInvoiceEnWaived() {
        Person director = persistPerson("director.scholarship4." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(5000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000004", course);
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(BigDecimal.valueOf(5000));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice = invoiceRepository.save(invoice);

        invoice.applyAdjustment(com.red_escuela.backend.billing.entity.InvoiceAdjustmentType.DISCOUNT,
                new com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue.PercentageAmount(BigDecimal.ONE), "Condonación total", director);
        invoiceRepository.save(invoice);

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.WAIVED);
    }

    // Test 81: una Invoice WAIVED con dueDate ya pasada no es tocada por el sweep de mora.
    @Test
    void unaCuotaWaivedNuncaRecibeRecargoAunqueVenza() {
        Person director = persistPerson("director.scholarship5." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(5000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000005", course);

        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(10);
        policy.setSurchargePercentage(BigDecimal.valueOf(0.10));
        policy.setSuspensionAfterDays(30);
        policy.setSuspensionAfterInvoiceCount(2);
        policy.setWithdrawalAfterDays(60);
        policy.setWithdrawalAfterInvoiceCount(3);
        policy.setEffectiveFrom(LocalDate.now().minusYears(1));
        policy.setCreatedBy(director);
        collectionsPolicyRepository.save(policy);

        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now().minusMonths(1));
        invoice.setDueDate(LocalDate.now().minusDays(20));
        invoice.setBaseAmount(BigDecimal.valueOf(5000));
        invoice.setStatus(InvoiceStatus.WAIVED);
        invoice = invoiceRepository.save(invoice);

        invoiceOverdueSweepService.run(LocalDate.now());

        Invoice reloaded = invoiceRepository.findByIdWithAdjustments(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.WAIVED);
        assertThat(reloaded.getAdjustments()).isEmpty();
    }

    @Test
    void otorgarBecaConMasCuotasQueLaDuracionDelCursoSeRechaza() {
        Person director = persistPerson("director.scholarship6." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000), 3);
        CourseEnrollment enrollment = persistCourseEnrollment("88000006", course);
        seleccionarPlanMensual(enrollment, director);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                        new CreateScholarshipRequest(new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.20)), "Beca 20%", 5)))
                .isInstanceOf(com.red_escuela.backend.billing.exception.ScholarshipExceedsCohortSpanException.class);
    }

    @Test
    void unSegundoOtorgamientoMientrasHayUnaBecaActivaSeRechazaPorElConstraintDeBase() {
        Person director = persistPerson("director.scholarship8." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000008", course);
        seleccionarPlanMensual(enrollment, director);

        grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                new CreateScholarshipRequest(new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), "Beca 10%", 3));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                        new CreateScholarshipRequest(new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.20)), "Beca 20%", 2)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void darDeBajaCancelaLosInstallmentsPendientesDeLaBeca() {
        Person director = persistPerson("director.scholarship7." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000007", course);
        seleccionarPlanMensual(enrollment, director);

        var scholarship = grantScholarshipForCourseUseCase.grant(enrollment.getId(),
                new CreateScholarshipRequest(new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.10)), "Beca 10%", 3));

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());
        courseEnrollmentService.darDeBaja(enrollment.getId());

        var reloaded = scholarshipRepository.findByIdWithInstallments(scholarship.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(com.red_escuela.backend.billing.entity.ScholarshipStatus.INACTIVE);
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.CANCELLED)
                .hasSize(2);
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.APPLIED)
                .hasSize(1);
    }

    // Fase 6.6: una beca otorgada DESPUÉS de que la cuota del período corriente ya se generó y
    // pagó no puede recuperar ese descuento vía un ajuste sobre esa Invoice (ya está PAID) — en
    // vez de eso genera una CreditNote por la diferencia y consume igual la primera installment.
    @Test
    void otorgarUnaBecaConLaCuotaDelMesYaPagaGeneraUnaNotaDeCreditoRetroactiva() {
        Person director = persistPerson("director.scholarship9." + System.nanoTime());
        Course course = persistCourse(director, BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = persistCourseEnrollment("88000009", course);
        seleccionarPlanMensual(enrollment, director);

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());
        Invoice cuotaDelMes = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).get(0);
        cuotaDelMes.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(cuotaDelMes);

        var scholarship = grantScholarshipForCourseUseCase.grant(enrollment.getId(), new CreateScholarshipRequest(
                new MonetaryAdjustmentValueRequest(null, BigDecimal.valueOf(0.20)), "Beca 20% tardía", 3));

        var creditNote = creditNoteRepository.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudent().getId()).get(0);
        assertThat(creditNote.getReasonType()).isEqualTo(com.red_escuela.backend.billing.entity.CreditNoteReasonType.RETROACTIVE_SCHOLARSHIP);
        assertThat(creditNote.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        var reloaded = scholarshipRepository.findByIdWithInstallments(scholarship.getId()).orElseThrow();
        assertThat(reloaded.getInstallments())
                .filteredOn(installment -> installment.getStatus() == com.red_escuela.backend.billing.entity.InstallmentStatus.APPLIED)
                .hasSize(1);
    }
}
