package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
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
import com.red_escuela.backend.service.ProgramEnrollmentService;

/**
 * Fase 6.1, decisión 2026-09-03 (cohortes): tests 16, 136-139. NO @Transactional a propósito:
 * InvoiceFactory.crearParaCarrera usa REQUIRES_NEW (ver su javadoc) — bajo un test
 * @Transactional, esa sub-transacción corre en otra conexión y no ve el fixture todavía sin
 * commitear. Por eso los helpers de acá persisten vía repository (cada save() es su propia
 * transacción, commiteada de una), mismo criterio que KarateFixtures.java.
 */
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
    private PaymentPlanOptionService paymentPlanOptionService;

    @Autowired
    private PaymentPlanSelectionService paymentPlanSelectionService;

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

    @AfterEach
    void limpiarContextoDeSeguridad() {
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

    private void autenticarComoPersona(Person person) {
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

    private Program persistPrograma(String nombre) {
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
        // Rango amplio (2 años) para que cualquier Invoice generada por un test con offsets de
        // hasta 12 meses desde "hoy" siga cayendo dentro del Term — monthsLeftInTemplate ahora
        // acota por Term.startDate/endDate (ver PaymentPlanSelectionService).
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
        revision.setMonthlyAmount(amount);
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

    // 136. alumnoQueSeMatriculaConAnticipaciónEnLaCohorteFirstDelAñoSiguienteNoRecibeCuotasAnteriores
    @Test
    void alumnoQueSeMatriculaConAnticipaciónEnLaCohorteFirstDelAñoSiguienteNoRecibeCuotasAnteriores() {
        Person director = persistPersonBase("DIR136" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera anticipada " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.of(2025, 8, 1), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100001");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        // Se matricula (paga la matrícula) en agosto 2025, meses antes de que arranque la cursada.
        billingScheduler.generarMatricula(enrollment);

        var invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(invoices.get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    // 137. matrículaDeLaCohorteSecondEsElCincuentaPorCientoAutomáticoDeLaFirst
    @Test
    void matrículaDeLaCohorteSecondEsElCincuentaPorCientoAutomáticoDeLaFirst() {
        Person director = persistPersonBase("DIR137" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera segunda cohorte " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohortSecond = persistCohort(template, CohortIntake.SECOND,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 1), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100002");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohortSecond);

        billingScheduler.generarMatricula(enrollment);

        var invoices = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(invoice.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(invoice.getAdjustments()).hasSize(1);
        assertThat(invoice.getAdjustments().get(0).getAppliedBy()).isNull();
    }

    // 138. matricularseFueraDeLaVentanaDeAdmisiónDeLaCohorteSeRechaza
    @Test
    void matricularseFueraDeLaVentanaDeAdmisiónDeLaCohorteSeRechaza() {
        Person director = persistPersonBase("DIR138" + System.nanoTime());
        Program program = persistPrograma("Carrera ventana cerrada " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), LocalDate.now(), director);

        assertThatThrownBy(() -> programEnrollmentService.validarVentanaDeAdmision(cohort))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);
    }

    // 139. dosCohortesDelMismoAñoTienenVentanasYCalendariosIndependientes
    @Test
    void dosCohortesDelMismoAñoTienenVentanasYCalendariosIndependientes() {
        Person director = persistPersonBase("DIR139" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera dos cohortes " + System.nanoTime());
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

        billingScheduler.generarMatricula(enrollmentA);
        billingScheduler.generarMatricula(enrollmentB);

        var invoicesA = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollmentA.getId());
        var invoicesB = invoiceRepository.findByProgramEnrollmentIdWithAdjustmentsOrderByPeriod(enrollmentB.getId());

        assertThat(invoicesA.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(invoicesA.get(0).getAdjustments()).isEmpty(); // FIRST no tiene descuento

        assertThat(invoicesB.get(0).getPeriod()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(invoicesB.get(0).getAdjustments()).hasSize(1); // SECOND sí
    }

    // 16. ajusteDePrecioAMitadDeAñoNoAfectaCuotasYaGeneradas
    @Test
    void ajusteDePrecioAMitadDeAñoNoAfectaCuotasYaGeneradas() {
        Person director = persistPersonBase("DIR16" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera ajuste de precio " + System.nanoTime());
        Term term = persistTerm(2026);
        ProgramYearBillingTemplate template = persistTemplate(program, term, 10, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 3, 1), director);
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), LocalDate.of(2020, 1, 1), director);

        Student student = persistStudent("81100005");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        Invoice invoiceVieja = invoiceFactory.crearParaCarrera(enrollment, null, FeeType.RECURRING_FEE, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 1));
        assertThat(invoiceVieja.getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // Ajuste de precio a mitad de año.
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(15000), LocalDate.of(2026, 6, 1), director);

        Invoice invoiceNueva = invoiceFactory.crearParaCarrera(enrollment, null, FeeType.RECURRING_FEE, YearMonth.of(2026, 7), LocalDate.of(2026, 7, 1));
        assertThat(invoiceNueva.getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(15000));

        Invoice recargada = invoiceRepository.findById(invoiceVieja.getId()).orElseThrow();
        assertThat(recargada.getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    // 37. scopeMonthsMayorAlQueQuedaDelAñoSeRechaza
    @Test
    void scopeMonthsMayorAlQueQuedaDelAñoSeRechaza() {
        Person director = persistPersonBase("DIR37" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera scope mayor " + System.nanoTime());
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
        PaymentPlanOption option = paymentPlanOptionService.crearParaCarrera(program.getId(), request);

        // La plantilla tiene 10 cuotas — pedir 12 excede lo que queda del año.
        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCarrera(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 12)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);
    }

    // 39. unAñoUpfrontCompletoYElSiguienteMensualSinArrastre
    @Test
    void unAñoUpfrontCompletoYElSiguienteMensualSinArrastre() {
        Person director = persistPersonBase("DIR39" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera upfront luego mensual " + System.nanoTime());
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
        PaymentPlanOption upfrontOption = paymentPlanOptionService.crearParaCarrera(program.getId(), upfrontRequest);
        paymentPlanSelectionService.seleccionarParaCarrera(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 10));

        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, mesInicial);
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(10);

        // Año 2 real (no solo un mes futuro dentro del mismo template — con el tope de plantilla
        // ahora exigido también en la generación, no alcanza con "otro mes", hace falta avanzar
        // de verdad vía avanzarAñoLectivo, mismo mecanismo que MultiYearProgramBillingTest):
        // plan mensual — no arrastra nada de lo ya generado en el año 1.
        Term term2 = persistTerm(2027);
        term2.setStartDate(mesInicial.plusMonths(10).atDay(1).atStartOfDay());
        term2.setEndDate(mesInicial.plusMonths(19).atEndOfMonth().atTime(23, 59));
        termRepository.save(term2);
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);
        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), template2.getId());
        // avanzarAñoLectivo persiste sobre una instancia propia, distinta de `enrollment` — sin
        // recargar, generarProximaCuotaParaCarrera seguiría viendo el currentBillingTemplate
        // viejo (null) y caería al template del año 1 vía programCohort.
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();

        CreatePaymentPlanOptionRequest monthlyRequest = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption monthlyOption = paymentPlanOptionService.crearParaCarrera(program.getId(), monthlyRequest);
        paymentPlanSelectionService.seleccionarParaCarrera(enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, mesInicial.plusMonths(10));

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(11);
    }

    // 40. cambioDeMonthlyAUpfrontAMitadDeAñoTambiénFunciona
    @Test
    void cambioDeMonthlyAUpfrontAMitadDeAñoTambiénFunciona() {
        Person director = persistPersonBase("DIR40" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera mensual a upfront " + System.nanoTime());
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
        PaymentPlanOption monthlyOption = paymentPlanOptionService.crearParaCarrera(program.getId(), monthlyRequest);
        paymentPlanSelectionService.seleccionarParaCarrera(enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 3; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, mesInicial.plusMonths(i));
        }

        // La selección nueva siempre arranca "hoy" (mismo mes que las 3 ya generadas por
        // MONTHLY) — el bloque UPFRONT de 7 meses se solapa con esos 3, que quedan como no-op
        // idempotente, y solo se generan los 4 meses que faltaban del bloque.
        CreatePaymentPlanOptionRequest upfrontRequest = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        PaymentPlanOption upfrontOption = paymentPlanOptionService.crearParaCarrera(program.getId(), upfrontRequest);
        paymentPlanSelectionService.seleccionarParaCarrera(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 7));
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, mesInicial);

        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)).hasSize(7);
    }

    // 21. alumnoConCarreraYCursoSimultáneosMantieneEstadosDeFacturaciónIndependientes
    @Test
    void alumnoConCarreraYCursoSimultáneosMantieneEstadosDeFacturaciónIndependientes() {
        Person director = persistPersonBase("DIR21" + System.nanoTime());
        autenticarComoPersona(director);

        Program program = persistPrograma("Carrera simultánea " + System.nanoTime());
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
        PaymentPlanOption programOption = paymentPlanOptionService.crearParaCarrera(program.getId(), programMonthly);
        paymentPlanSelectionService.seleccionarParaCarrera(programEnrollment.getId(), new SelectPaymentPlanRequest(programOption.getId(), null));

        // Un curso corto, independiente, para el mismo alumno.
        Term courseTerm = persistTerm(2026);
        com.red_escuela.backend.entity.Course course = new com.red_escuela.backend.entity.Course();
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
        courseRevision.setMonthlyAmount(BigDecimal.valueOf(5000));
        courseRevision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        courseRevision.setCreatedBy(director);
        priceRevisionRepository.save(courseRevision);

        com.red_escuela.backend.entity.CourseEnrollment courseEnrollment = new com.red_escuela.backend.entity.CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(com.red_escuela.backend.entity.EnrollmentStatus.ENROLLED);
        courseEnrollment.setEnrollmentDate(java.time.LocalDateTime.now());
        courseEnrollment = courseEnrollmentRepository.save(courseEnrollment);

        CreatePaymentPlanOptionRequest courseMonthly = new CreatePaymentPlanOptionRequest(
                "Mensual curso", PaymentPlanCadence.MONTHLY, null, false, null, null);
        PaymentPlanOption courseOption = paymentPlanOptionService.crearParaCurso(course.getId(), courseMonthly);
        paymentPlanSelectionService.seleccionarParaCurso(courseEnrollment.getId(), new SelectPaymentPlanRequest(courseOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        invoiceGenerationService.generarProximaCuotaParaCarrera(programEnrollment, mesInicial);
        invoiceGenerationService.generarProximaCuotaParaCurso(courseEnrollment, mesInicial);

        // La matrícula de la carrera y la cuota del curso son totalmente independientes: cada
        // una en su propia Invoice, sin acoplamiento entre courseEnrollment y programEnrollment.
        var invoicesCarrera = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId());
        var invoicesCurso = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId());

        assertThat(invoicesCarrera).extracting("feeType").containsExactlyInAnyOrder(FeeType.ENROLLMENT_FEE, FeeType.RECURRING_FEE);
        assertThat(invoicesCurso).extracting("feeType").containsExactly(FeeType.RECURRING_FEE);
        assertThat(invoicesCurso.get(0).getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(invoicesCarrera).noneMatch(i -> i.getCourseEnrollment() != null);
        assertThat(invoicesCurso).noneMatch(i -> i.getProgramEnrollment() != null);
    }

    @Autowired
    private com.red_escuela.backend.repository.CourseRepository courseRepository;

    @Autowired
    private com.red_escuela.backend.repository.CourseEnrollmentRepository courseEnrollmentRepository;
}
