package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6.3: tests 7, 8, 9 (mora de un mes suspende, dos meses da de baja, pagar antes de la baja
 * la evita), 132-134 (criterio doble: antigüedad O cantidad de cuotas OVERDUE) y 3/5 (billingStanding
 * consistente con una mezcla de cuotas pagas y pendientes-no-vencidas). NO @Transactional a
 * propósito: cada enrollment se procesa en su propia transacción real
 * (EnrollmentBillingComplianceRowProcessor, REQUIRES_NEW).
 */
@SpringBootTest
@ActiveProfiles("test")
class EnrollmentBillingComplianceSweepServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EnrollmentBillingComplianceSweepService sweepService;

    @Autowired
    private InvoiceRepository invoiceRepository;

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
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private ProgramEnrollment persistProgramEnrollment(String dni) {
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
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(ProgramEnrollment enrollment, BigDecimal amount, LocalDate dueDate, InvoiceStatus status) {
        return persistInvoice(enrollment, amount, dueDate, YearMonth.from(dueDate), status);
    }

    // period explícito y distinto de dueDate: dos Invoice OVERDUE simultáneas del mismo
    // enrollment necesitan un period distinto cada una (uk_invoice_billable_period), aunque
    // ambas venzan dentro del mismo mes calendario real.
    private Invoice persistInvoice(ProgramEnrollment enrollment, BigDecimal amount, LocalDate dueDate, YearMonth period, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private CollectionsPolicy persistPolicy(Person createdBy, int suspensionAfterDays, int suspensionAfterInvoiceCount,
                                             int withdrawalAfterDays, int withdrawalAfterInvoiceCount) {
        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(10);
        policy.setSurchargePercentage(BigDecimal.valueOf(0.10));
        policy.setSuspensionAfterDays(suspensionAfterDays);
        policy.setSuspensionAfterInvoiceCount(suspensionAfterInvoiceCount);
        policy.setWithdrawalAfterDays(withdrawalAfterDays);
        policy.setWithdrawalAfterInvoiceCount(withdrawalAfterInvoiceCount);
        policy.setEffectiveFrom(LocalDate.now().minusYears(1));
        policy.setCreatedBy(createdBy);
        return collectionsPolicyRepository.save(policy);
    }

    private ProgramEnrollment reload(Long id) {
        return programEnrollmentRepository.findById(id).orElseThrow();
    }

    @Test
    void alumnoConMoraDeUnMesQuedaSuspendido() {
        Person director = persistPerson("director.compliance1." + System.nanoTime());
        persistPolicy(director, 30, 2, 60, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000101");
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(31), InvoiceStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);
    }

    @Test
    void alumnoSuspendidoSinPagarDosMesesEsDadoDeBajaAutomaticamente() {
        Person director = persistPerson("director.compliance2." + System.nanoTime());
        persistPolicy(director, 30, 2, 60, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000102");
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(61), InvoiceStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        ProgramEnrollment reloaded = reload(enrollment.getId());
        assertThat(reloaded.getBillingStanding()).isEqualTo(EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
    }

    @Test
    void alumnoSuspendidoQuePagaAntesDeDosMesesVuelveAAlDiaSinDarseDeBaja() {
        Person director = persistPerson("director.compliance3." + System.nanoTime());
        persistPolicy(director, 30, 2, 60, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000103");
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(31), InvoiceStatus.OVERDUE);

        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        sweepService.run(LocalDate.now());

        ProgramEnrollment reloaded = reload(enrollment.getId());
        assertThat(reloaded.getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    // Test 132 de la spec (el nombre original de la spec dice "tres", pero su propia descripción
    // en prosa pide "un alumno con 2 Invoice OVERDUE recientes" con suspensionAfterInvoiceCount=2
    // — inconsistencia de la spec misma, no del test; se corrige acá el nombre del método para que
    // coincida con lo que realmente se ejercita, auditoría de calidad de tests 2026-09-04).
    @Test
    void dosInvoicesOverdueSimultaneasSuspendenAunqueNingunaSeaMuyVieja() {
        Person director = persistPerson("director.compliance4." + System.nanoTime());
        persistPolicy(director, 30, 2, 60, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000104");
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(10), YearMonth.now(), InvoiceStatus.OVERDUE);
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoiceStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);
    }

    @Test
    void unaSolaInvoiceMuyViejaSuspendeAunqueSeaLaUnicaOverdue() {
        Person director = persistPerson("director.compliance5." + System.nanoTime());
        persistPolicy(director, 30, 5, 60, 6);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000105");
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(35), InvoiceStatus.OVERDUE);

        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);
    }

    @Test
    void unPagoParcialDeUnaDeLasCuotasQueSuspendieronNoLevantaLaSuspensionSola() {
        Person director = persistPerson("director.compliance6." + System.nanoTime());
        persistPolicy(director, 30, 2, 60, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000106");
        Invoice first = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(10), YearMonth.now(), InvoiceStatus.OVERDUE);
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoiceStatus.OVERDUE);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);

        first.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(first);
        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);
    }

    // Regresión (auditoría 2026-09-04): antes, la condición de "cero deuda" para volver a
    // UP_TO_DATE miraba Invoice PENDING u OVERDUE — un enrollment SUSPENDED que saldaba TODA su
    // mora pero tenía una Invoice PENDING futura ya generada (plan UPFRONT, o matrícula+primera
    // cuota nacidas juntas) nunca veía "cero" y quedaba SUSPENDED para siempre.
    @Test
    void saldarTodaLaMoraLevantaLaSuspensionAunqueHayaUnaCuotaFuturaPendienteTodaviaNoVencida() {
        Person director = persistPerson("director.compliance9." + System.nanoTime());
        persistPolicy(director, 10, 2, 30, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000109");
        Invoice overdue = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusDays(15), YearMonth.now().minusMonths(1), InvoiceStatus.OVERDUE);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.SUSPENDED);

        overdue.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(overdue);
        // Cuota futura ya generada (ej. bloque UPFRONT), todavía dentro de su propio período —
        // no debería impedir que la suspensión se levante, porque no es deuda vencida.
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().plusDays(20), YearMonth.now().plusMonths(1), InvoiceStatus.PENDING);
        sweepService.run(LocalDate.now());

        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);
    }

    @Test
    void alumnoQueCambiaDePlanDePagoAMitadDeCarreraSigueAlDiaConsistentemente() {
        Person director = persistPerson("director.compliance7." + System.nanoTime());
        persistPolicy(director, 10, 2, 30, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000107");

        // Primer semestre (simulando UPFRONT ya pagado completo): 5 cuotas, todas PAID.
        YearMonth base = YearMonth.now().minusMonths(6);
        for (int i = 0; i < 5; i++) {
            persistInvoice(enrollment, BigDecimal.valueOf(1000), base.plusMonths(i).atDay(1), InvoiceStatus.PAID);
        }
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);

        // Segundo semestre (simulando MONTHLY mes a mes): la cuota del mes actual todavía no
        // venció — billingStanding tiene que seguir UP_TO_DATE, no hay nada vencido.
        Invoice monthlyInvoice = persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().plusDays(5), InvoiceStatus.PENDING);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);

        // Un mes después, esa cuota ya se pagó y nace la siguiente, tampoco vencida todavía.
        monthlyInvoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(monthlyInvoice);
        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().plusMonths(1).plusDays(5), InvoiceStatus.PENDING);
        sweepService.run(LocalDate.now().plusMonths(1));
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);
    }

    // Test 5 de la spec. Corrección 2026-09-04 (auditoría de calidad de tests): el nombre original
    // ("...SinImportarCombinacionDePlanes") sugería variar tipo de plan (MONTHLY/UPFRONT) — esa
    // variación real la cubre alumnoQueCambiaDePlanDePagoAMitadDeCarreraSigueAlDiaConsistentemente
    // (arriba); este test solo verifica una progresión lineal de cuotas pagas + una pendiente
    // futura consultada en tres momentos distintos, sin variar el tipo de plan.
    @Test
    void billingStandingSeMantieneUpToDateConsultadoEnTresMomentosDistintos() {
        Person director = persistPerson("director.compliance8." + System.nanoTime());
        persistPolicy(director, 10, 2, 30, 3);
        ProgramEnrollment enrollment = persistProgramEnrollment("99000108");

        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusMonths(2), InvoiceStatus.PAID);
        sweepService.run(LocalDate.now().minusMonths(2));
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);

        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().minusMonths(1), InvoiceStatus.PAID);
        sweepService.run(LocalDate.now().minusMonths(1));
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);

        persistInvoice(enrollment, BigDecimal.valueOf(1000), LocalDate.now().plusDays(5), InvoiceStatus.PENDING);
        sweepService.run(LocalDate.now());
        assertThat(reload(enrollment.getId()).getBillingStanding()).isEqualTo(EnrollmentBillingStanding.UP_TO_DATE);
    }
}
