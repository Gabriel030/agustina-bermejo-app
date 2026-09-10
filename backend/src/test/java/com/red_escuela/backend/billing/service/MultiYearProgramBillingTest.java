package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.repository.AddressRepository;
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

/**
 * Decisión 2026-09-03 (progresión multi-año): carreras de 2, 2.5, 3 y 3.5 años, avance de año,
 * repetición de año, atraso, e intercalado de forma de pago dentro y entre años. NO
 * @Transactional a propósito — mismo motivo que ProgramCohortBillingTest (InvoiceFactory usa
 * REQUIRES_NEW).
 *
 * Todos los períodos se anclan a YearMonth.now() y avanzan siempre hacia adelante — nunca hacia
 * atrás — porque PaymentPlanSelectionRepository.findActiveForProgramEnrollment exige
 * effectiveFrom <= period, y effectiveFrom siempre se fija a "hoy" real al seleccionar un plan
 * (ver PaymentPlanSelectionService.seleccionarParaCarrera). Por el mismo motivo, un bloque UPFRONT
 * siempre nace anclado a "hoy" (no a un período simulado futuro) — así que, para evitar que dos
 * años simulados choquen contra el mismo rango de períodos (Invoice es idempotente por
 * (programEnrollment, feeType, period): un choque no lanza error, simplemente no crea nada nuevo),
 * cada año usa un bloque de meses disjunto del resto.
 */
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
    private TermRepository termRepository;

    @Autowired
    private ProgramYearBillingTemplateRepository templateRepository;

    @Autowired
    private ProgramCohortRepository programCohortRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

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

    // first/last son inclusive — el Term queda acotado EXACTAMENTE a esos meses, sin margen, para
    // que dos años consecutivos (first/last disjuntos) nunca se superpongan en
    // PaymentPlanSelectionService.monthsLeftInTemplate.
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

    private void persistPriceRevision(Program program, FeeType feeType, BigDecimal amount, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setFeeType(feeType);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(amount);
        revision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
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

    private long tuitionInvoiceCount(Long programEnrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)
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
                new CreatePaymentPlanOptionRequest("Adelantado", PaymentPlanCadence.UPFRONT, null, true,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(60)));
    }

    // Carrera de 2 años: 2 ProgramYearBillingTemplate (10 cuotas c/u), avanza de año una vez,
    // ambos años en MONTHLY — total 20 cuotas RECURRING_FEE, cada una independiente de la otra.
    @Test
    void twoYearCareerGeneratesEachYearsInvoicesSeparately() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIR2Y" + System.nanoTime());
        Program program = persistProgram("Carrera 2 años " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(10000), director);
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
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(10);

        // Avanza al año 2 — programCohort NO cambia, solo currentBillingTemplate. Recargar es
        // obligatorio: avanzarAñoLectivo persiste sobre una instancia propia (findOrThrow
        // interno), distinta de esta variable — sin recargar, generarProximaCuotaParaCarrera
        // seguiría viendo currentBillingTemplate=null y caería al template del año 1.
        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), templateAño2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(20);

        ProgramEnrollment recargado = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(recargado.getProgramCohort().getId()).isEqualTo(cohortAño1.getId());
        assertThat(recargado.getCurrentBillingTemplate().getId()).isEqualTo(templateAño2.getId());
    }

    // Carrera de 2 años y medio: año 3 es una plantilla "corta" (5 cuotas, medio año).
    @Test
    void twoAndHalfYearCareerLastYearHasFewerInvoices() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIR25Y" + System.nanoTime());
        Program program = persistProgram("Carrera 2.5 años " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(8000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);

        Term term3 = persistTerm(3, base.plusMonths(20), base.plusMonths(24));
        ProgramYearBillingTemplate template3Corto = persistTemplate(program, term3, 5, director); // medio año

        Student student = persistStudent("83000002");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(i));
        }

        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), template2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(10 + i));
        }

        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), template3Corto.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(20 + i));
        }

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(25); // 10 + 10 + 5
    }

    // Repetir un año NO acumula contra el total de la carrera: el remanente UPFRONT del año
    // repetido se mide contra SU PROPIA plantilla, no contra lo ya facturado en años anteriores.
    // El bloque UPFRONT del año repetido se ancla a "hoy" (base), así que el año 1 se simula
    // deliberadamente MÁS ADELANTE en el calendario (base+10..+19) para no chocar contra él.
    @Test
    void studentRepeatingAYearDoesNotAccumulateAgainstCareerTotal() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRREP" + System.nanoTime());
        Program program = persistProgram("Carrera con repitente " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(9000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        // "Repite el año 1": una plantilla nueva, con el mismo contenido de cuotas — el alumno
        // vuelve a cursar las mismas materias.
        Term termRepite = persistTerm(2, base.minusMonths(1), base.plusMonths(9));
        ProgramYearBillingTemplate templateRepite = persistTemplate(program, termRepite, 10, director);

        Student student = persistStudent("83000003");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(10);

        // Repite: avanza a la plantilla de repetición y pide TODO el año adelantado (10 meses).
        // Si el remanente se calculara contra el total de la carrera (10 ya generadas + 10
        // nuevas) esto se rechazaría por error — tiene que aceptarse, porque se mide únicamente
        // contra la plantilla de ESTE año.
        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), templateRepite.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        PaymentPlanOption adelantado = upfrontPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(adelantado.getId(), 10));
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base);

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(20); // 10 del año 1 + 10 de la repetición
    }

    // Alumno intercala: paga los primeros meses de un año MONTHLY, después adelanta el resto de
    // ESE MISMO año por UPFRONT (que se ancla a "hoy" y por lo tanto se superpone de forma
    // idempotente con lo ya generado — mismo comportamiento que
    // cambioDeMonthlyAUpfrontAMitadDeAñoTambiénFunciona en ProgramCohortBillingTest), y al año
    // siguiente vuelve a MONTHLY — sin arrastre entre años.
    @Test
    void studentAlternatesPaymentMethodsWithinAndAcrossYearsNoCarryover() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRINT" + System.nanoTime());
        Program program = persistProgram("Carrera intercalada " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(7000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(19));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 10, director);

        Student student = persistStudent("83000004");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        // Año 1: 4 meses por MONTHLY...
        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 4; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(4);

        // ...y decide adelantar los 6 meses que le quedan del año 1 (10 - 4 = 6). El bloque
        // UPFRONT nace en "hoy" (base) y se superpone con los 4 ya generados (base..base+3) —
        // esos 4 quedan como no-op idempotente, solo se crean 2 nuevos (base+4, base+5): el total
        // del año 1 queda en 6, no en 4+6.
        PaymentPlanOption adelantado = upfrontPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(adelantado.getId(), 6));
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base);
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(6);

        // Año 2: vuelve a MONTHLY, arranca fresco (remanente = 10, no 4).
        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), template2.getId());
        enrollment = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        PaymentPlanOption mensualAño2 = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensualAño2.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(10 + i));
        }
        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(16); // 6 del año 1 + 10 del año 2
    }

    // Pedir UPFRONT por más de lo que queda del año CORRIENTE se rechaza, incluso si el alumno
    // ya venía de un año anterior completo — el tope nunca es "total de la carrera menos esto".
    @Test
    void requestingMoreThanCurrentYearRemainderIsRejectedEvenAfterACompleteYear() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRTOPE" + System.nanoTime());
        Program program = persistProgram("Carrera con tope por año " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(6000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term1 = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template1 = persistTemplate(program, term1, 10, director);
        ProgramCohort cohort1 = persistCohort(template1, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Term term2 = persistTerm(2, base.plusMonths(10), base.plusMonths(18));
        ProgramYearBillingTemplate template2 = persistTemplate(program, term2, 8, director); // año 2 más corto

        Student student = persistStudent("83000005");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort1);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));
        for (int i = 0; i < 10; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(i));
        }

        programEnrollmentService.avanzarAñoLectivo(enrollment.getId(), template2.getId());
        PaymentPlanOption adelantado = upfrontPlan(program, director);
        authenticateAs(director);

        // El año 2 solo tiene 8 cuotas de plantilla — pedir 9 tiene que rechazarse, aunque el
        // alumno ya haya facturado 10 en el año anterior sin ningún problema.
        assertThatThrownBy(() -> selectPaymentPlanForProgramUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(adelantado.getId(), 9)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);
    }

    // Gap real (2026-09-03): antes de RecurringFeeWindowService, MONTHLY no tenía ningún tope
    // de cuotas ni de fecha para una carrera — solo se validaba una vez, al ELEGIR un plan
    // UPFRONT, nunca en la generación real. Un cron mensual futuro habría seguido generando
    // cuotas más allá de recurringFeeInvoicesCount, o fuera del cuatrimestre, indefinidamente.
    @Test
    void monthlyGenerationBeyondTemplateCapIsSilentNoOp() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRTOPEMENS" + System.nanoTime());
        Program program = persistProgram("Carrera con tope mensual " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(5000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        Term term = persistTerm(1, base, base.plusMonths(9));
        ProgramYearBillingTemplate template = persistTemplate(program, term, 4, director); // cuatrimestre
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Student student = persistStudent("83000006");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        // Pide 6 meses aunque la plantilla del cohorte solo tiene 4 — los 2 de más no se crean.
        for (int i = 0; i < 6; i++) {
            invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(i));
        }

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(4);
    }

    // Mismo gap, ahora por FECHA: un período fuera del Term del cohorte corriente no genera
    // cuota, aunque no se haya llegado todavía al tope de recurringFeeInvoicesCount.
    @Test
    void monthlyGenerationOutsideCohortWindowIsSilentNoOp() {
        YearMonth base = YearMonth.now();
        Person director = persistPerson("DIRVENTMENS" + System.nanoTime());
        Program program = persistProgram("Carrera con ventana mensual " + System.nanoTime());
        persistPriceRevision(program, FeeType.RECURRING_FEE, BigDecimal.valueOf(5000), director);
        persistPriceRevision(program, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(1), director);

        // Term de 5 meses (base..base+4) con una plantilla de 3 cuotas — el tope de plantilla
        // (ProgramYearBillingTemplate ahora se autovalida contra la duración del Term, así que
        // no podría configurarse un tope de cuotas MAYOR a esos 5 meses; para aislar el rechazo
        // por FECHA del rechazo por CUPO, acá el tope de 3 queda deliberadamente sin alcanzar
        // cuando se pide un período fuera de ventana).
        Term term = persistTerm(1, base, base.plusMonths(4));
        ProgramYearBillingTemplate template = persistTemplate(program, term, 3, director);
        ProgramCohort cohort = persistCohort(template, CohortIntake.FIRST,
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(1), LocalDate.now(), director);

        Student student = persistStudent("83000007");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, cohort);

        PaymentPlanOption mensual = monthlyPlan(program, director);
        authenticateAs(director);
        selectPaymentPlanForProgramUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        // Un solo mes dentro de la ventana (1 de 3 del tope, todavía queda cupo de sobra) y
        // después un período bien afuera de la ventana del Term — el rechazo tiene que venir
        // de la FECHA, no del cupo (que ni de cerca se alcanzó).
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base);
        invoiceGenerationService.generarProximaCuotaParaCarrera(enrollment, base.plusMonths(10));

        assertThat(tuitionInvoiceCount(enrollment.getId())).isEqualTo(1);
    }
}
