package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.PaymentPlanCadence;
import com.red_escuela.backend.entity.PaymentPlanOption;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PriceRevision;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.PriceRevisionRepository;
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

/**
 * Fase 6.1: tests 4 (UPFRONT/MONTHLY), 29-34 (ventana UPFRONT), 35-38 (scopeMonths), 41
 * (constraint anti-duplicación). NO @Transactional a propósito — ver ProgramCohortBillingTest.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentPlanSelectionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentPlanOptionService paymentPlanOptionService;

    @Autowired
    private PaymentPlanSelectionService paymentPlanSelectionService;

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
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

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

    private Course persistCourse(int durationMonths, BigDecimal price, Person director) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(durationMonths);
        course = courseRepository.save(course);

        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(FeeType.RECURRING_FEE);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(price);
        revision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        revision.setCreatedBy(director);
        priceRevisionRepository.save(revision);

        return course;
    }

    private CourseEnrollment persistCourseEnrollment(Student student, Course course) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(java.time.LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private PaymentPlanOption persistUpfrontOption(Course course, boolean windowEnabled, LocalDate start, LocalDate end, Person director) {
        autenticarComoPersona(director);
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, windowEnabled, start, end);
        return paymentPlanOptionService.crearParaCurso(course.getId(), request);
    }

    private PaymentPlanOption persistMonthlyOption(Course course, Person director) {
        autenticarComoPersona(director);
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);
        return paymentPlanOptionService.crearParaCurso(course.getId(), request);
    }

    // 4. cursoDeSeisMesesOfreceUnaCuotaOSeisCuotasSegúnElPlanElegido
    @Test
    void cursoDeSeisMesesOfreceUnaCuotaOSeisCuotasSegúnElPlanElegido() {
        Person director = persistPersonBase("DIR4" + System.nanoTime());

        Course courseMonthly = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentMonthly = persistStudent("82200001");
        CourseEnrollment enrollmentMonthly = persistCourseEnrollment(studentMonthly, courseMonthly);
        PaymentPlanOption monthlyOption = persistMonthlyOption(courseMonthly, director);
        autenticarComoPersona(director);
        paymentPlanSelectionService.seleccionarParaCurso(enrollmentMonthly.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 6; i++) {
            invoiceGenerationService.generarProximaCuotaParaCurso(enrollmentMonthly, mesInicial.plusMonths(i));
        }
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollmentMonthly.getId())).hasSize(6);

        Course courseUpfront = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentUpfront = persistStudent("82200002");
        CourseEnrollment enrollmentUpfront = persistCourseEnrollment(studentUpfront, courseUpfront);
        PaymentPlanOption upfrontOption = persistUpfrontOption(courseUpfront, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        autenticarComoPersona(director);
        paymentPlanSelectionService.seleccionarParaCurso(enrollmentUpfront.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 6));

        invoiceGenerationService.generarProximaCuotaParaCurso(enrollmentUpfront, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollmentUpfront.getId())).hasSize(6);
    }

    // 29. opciónDeshabilitadaRechazaLaElecciónSiempre
    @Test
    void opciónDeshabilitadaRechazaLaElecciónSiempre() {
        Person director = persistPersonBase("DIR29" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200003");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, false, null, null, director);
        autenticarComoPersona(director);

        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    // 30. fechaAntesDeLaVentanaRechaza
    @Test
    void fechaAntesDeLaVentanaRechaza() {
        Person director = persistPersonBase("DIR30" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200004");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), director);
        autenticarComoPersona(director);

        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    // 31. fechaDespuésDeLaVentanaRechaza
    @Test
    void fechaDespuésDeLaVentanaRechaza() {
        Person director = persistPersonBase("DIR31" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200005");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(20), LocalDate.now().minusDays(10), director);
        autenticarComoPersona(director);

        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    // 32. bordeInclusiveInicioYFinDeVentanaAceptan — corrección 2026-09-04 (auditoría de calidad
    // de tests): la versión original solo probaba el borde de INICIO (hoy == upfrontWindowStart);
    // el borde de FIN (hoy == upfrontWindowEnd) quedaba sin ejercitar pese a que el nombre lo
    // promete — un error de borde en `today.isAfter(option.getUpfrontWindowEnd())` (ej. cambiado
    // por error a una comparación no-inclusive del lado equivocado) no se hubiera detectado.
    @Test
    void bordeInclusiveInicioDeVentanaAcepta() {
        Person director = persistPersonBase("DIR32" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200006");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        // hoy == upfrontWindowStart
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now(), LocalDate.now().plusDays(10), director);
        autenticarComoPersona(director);

        var selection = paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6));
        assertThat(selection).isNotNull();
        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(option.getId());
    }

    @Test
    void bordeInclusiveFinDeVentanaAcepta() {
        Person director = persistPersonBase("DIR32B" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200019");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        // hoy == upfrontWindowEnd
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(10), LocalDate.now(), director);
        autenticarComoPersona(director);

        var selection = paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6));
        assertThat(selection).isNotNull();
        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(option.getId());
    }

    // 35. scopeMonthsIgualATodoElAñoGeneraElAñoCompletoDeUna
    @Test
    void scopeMonthsIgualATodoElAñoGeneraElAñoCompletoDeUna() {
        Person director = persistPersonBase("DIR35" + System.nanoTime());
        Course course = persistCourse(10, BigDecimal.valueOf(10000), director);
        Student student = persistStudent("82200007");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        autenticarComoPersona(director);

        paymentPlanSelectionService.seleccionarParaCurso(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 10));
        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(10);
    }

    // 38. scopeMonthsCeroONegativoSeRechaza
    @Test
    void scopeMonthsCeroONegativoSeRechaza() {
        Person director = persistPersonBase("DIR38" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200008");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        autenticarComoPersona(director);

        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 0)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);

        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), -3)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);
    }

    // 41. Corrección 2026-09-04 (auditoría de calidad de tests): el nombre original
    // ("bloqueUpfrontYCorridaMonthlyPosteriorNoDuplican") prometía una transición UPFRONT→MONTHLY,
    // pero PaymentPlanSelectionService.seleccionarParaCurso lanza CoursePaymentPlanAlreadySetException
    // si ya existe una selección abierta para ese CourseEnrollment ("A course's payment plan is
    // unique and immutable for the life of the enrollment", línea 70) — a diferencia de una carrera
    // (ver ProgramCohortBillingTest.cambioDeMonthlyAUpfrontAMitadDeAñoTambiénFunciona), un curso NO
    // admite cambiar de plan de pago, así que esa transición es arquitectónicamente imposible acá.
    // Lo que el test realmente ejercitaba (y sigue ejercitando, ahora con el nombre correcto) es la
    // idempotencia de volver a correr la generación sobre el mismo mes.
    @Test
    void correrDosVecesElMismoMesSobreUnBloqueUpfrontNoDuplica() {
        Person director = persistPersonBase("DIR41" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200009");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption upfrontOption = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        autenticarComoPersona(director);

        paymentPlanSelectionService.seleccionarParaCurso(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 5));
        YearMonth mesInicial = YearMonth.now();
        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);

        // Repetir la misma corrida sobre el mismo mes — no debe lanzar, ni duplicar filas.
        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);
    }

    // 33. dosCarrerasConVentanasDistintasNoSeInterfieren
    @Test
    void dosCarrerasConVentanasDistintasNoSeInterfieren() {
        Person director = persistPersonBase("DIR33" + System.nanoTime());

        Course courseA = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentA = persistStudent("82200010");
        CourseEnrollment enrollmentA = persistCourseEnrollment(studentA, courseA);
        PaymentPlanOption optionA = persistUpfrontOption(courseA, true, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30), director);

        Course courseB = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentB = persistStudent("82200011");
        CourseEnrollment enrollmentB = persistCourseEnrollment(studentB, courseB);
        PaymentPlanOption optionB = persistUpfrontOption(courseB, true, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), director);

        // "Hoy" queda fuera de las dos ventanas (mayo) — ambas tienen que rechazar por separado,
        // confirmando que la ventana es por PaymentPlanOption, no un valor global.
        autenticarComoPersona(director);
        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollmentA.getId(), new SelectPaymentPlanRequest(optionA.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollmentB.getId(), new SelectPaymentPlanRequest(optionB.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    // 34. opciónMonthlyNuncaSeVeAfectadaPorLaVentana
    @Test
    void opciónMonthlyNuncaSeVeAfectadaPorLaVentana() {
        Person director = persistPersonBase("DIR34" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200012");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption monthlyOption = persistMonthlyOption(course, director);
        autenticarComoPersona(director);

        // MONTHLY nunca tiene ventana propia — elegirla siempre funciona.
        var selection = paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));
        assertThat(selection).isNotNull();
    }

    // 36. scopeMonthsMenorGeneraSoloEseTramo
    @Test
    void scopeMonthsMenorGeneraSoloEseTramo() {
        Person director = persistPersonBase("DIR36" + System.nanoTime());
        Course course = persistCourse(10, BigDecimal.valueOf(10000), director);
        Student student = persistStudent("82200013");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        autenticarComoPersona(director);

        paymentPlanSelectionService.seleccionarParaCurso(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 5));
        invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);
    }

    // Gap real (2026-09-03): antes de RecurringFeeWindowService, MONTHLY no tenía ningún tope
    // de cuotas para un curso — un cron mensual futuro habría seguido generando cuotas más allá
    // de durationMonths indefinidamente. Blindado acá contra ese caso.
    @Test
    void generarMensualMasAlláDeLaDuraciónDelCursoEsNoOpSilencioso() {
        Person director = persistPersonBase("DIRDUR" + System.nanoTime());
        Course course = persistCourse(3, BigDecimal.valueOf(12000), director);
        Student student = persistStudent("82200014");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);
        autenticarComoPersona(director);
        paymentPlanSelectionService.seleccionarParaCurso(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        YearMonth base = YearMonth.now();
        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, base.plusMonths(i));
        }

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(3);
    }

    // Mismo gap, ahora por FECHA en vez de por cantidad: un curso con Term acotado no puede
    // facturar RECURRING_FEE para un período fuera de esa ventana, aunque no se haya llegado al tope
    // de durationMonths todavía.
    @Test
    void generarMensualFueraDeLaVentanaDelCursoEsNoOpSilencioso() {
        Person director = persistPersonBase("DIRVENT" + System.nanoTime());
        YearMonth base = YearMonth.now();
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(base.atDay(1).atStartOfDay());
        term.setEndDate(base.plusMonths(2).atEndOfMonth().atTime(23, 59));
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso con ventana " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(6); // sin tope de cantidad — el corte tiene que ser por fecha
        course = courseRepository.save(course);

        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(FeeType.RECURRING_FEE);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(BigDecimal.valueOf(12000));
        revision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        revision.setCreatedBy(director);
        priceRevisionRepository.save(revision);

        Student student = persistStudent("82200015");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);
        autenticarComoPersona(director);
        paymentPlanSelectionService.seleccionarParaCurso(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        // El Term solo cubre 3 meses (base..base+2) aunque durationMonths permitiría 6.
        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generarProximaCuotaParaCurso(enrollment, base.plusMonths(i));
        }

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(3);
    }

    // ---------- decisión 2026-09-04: autoservicio del alumno en payment-plan ----------

    @Test
    void unAlumnoPuedeElegirSuPropioPlanDePago() {
        Person director = persistPersonBase("DIROWN1" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200016");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);

        autenticarComoPersona(student.getPerson());
        var selection = paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        // Corrección 2026-09-04 (auditoría de calidad de tests): el único assert previo
        // (isNotNull()) solo confirmaba que no se lanzó excepción, sin verificar ningún campo real
        // de la selección creada.
        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(mensual.getId());
        assertThat(selection.getCourseEnrollment().getId()).isEqualTo(enrollment.getId());
        assertThat(selection.getCreatedBy().getId()).isEqualTo(student.getPerson().getId());
    }

    @Test
    void unAlumnoNoPuedeElegirElPlanDePagoDeOtroAlumno() {
        Person director = persistPersonBase("DIROWN2" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student dueño = persistStudent("82200017");
        Student intruso = persistStudent("82200018");
        CourseEnrollment enrollment = persistCourseEnrollment(dueño, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);

        autenticarComoPersona(intruso.getPerson());
        assertThatThrownBy(() -> paymentPlanSelectionService.seleccionarParaCurso(
                enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null)))
                .isInstanceOf(com.red_escuela.backend.exception.NotOwnEnrollmentException.class);
    }
}
