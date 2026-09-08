package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Épica 7 Fase 3: solicitud de alta en carrera con pago de matrícula bloqueante
 * (Invoice+PaymentReceipt en la misma request que solicitar) y aprobación conjunta
 * de la solicitud y el comprobante en una sola transacción. PaymentReceiptService es
 * un placeholder mínimo hasta Fase 6.2 (Pagos y allocations) — alcanza con verificar
 * que la Invoice pasa a PAID, sin un Payment real todavía.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramEnrollmentApplicationUseCaseTest extends AbstractIntegrationTest {

    @Autowired
    private RequestProgramEnrollmentApplicationUseCase requestProgramEnrollmentApplicationUseCase;

    @Autowired
    private ApproveProgramEnrollmentApplicationUseCase approveProgramEnrollmentApplicationUseCase;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent(String dni) {
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistPrograma(String nombre) {
        Program program = new Program();
        program.setName(nombre);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private ProgramCohort persistCohort(Program program, LocalDate admissionStart, LocalDate admissionEnd, Person createdBy) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(10);
        template.setCreatedBy(createdBy);
        entityManager.persist(template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(admissionStart == null ? LocalDate.now().minusDays(30) : admissionStart);
        cohort.setAdmissionEnd(admissionEnd == null ? LocalDate.now().plusDays(30) : admissionEnd);
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(createdBy);
        entityManager.persist(cohort);
        return cohort;
    }

    private void persistPriceRevision(Program program, BigDecimal amount, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setFeeType(FeeType.ENROLLMENT_FEE);
        revision.setDescription("Matrícula de prueba");
        revision.setMonthlyAmount(amount);
        revision.setEffectiveFrom(LocalDate.now().minusDays(1));
        revision.setCreatedBy(createdBy);
        entityManager.persist(revision);
    }

    private MultipartFile comprobante() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void solicitarConPagoCreaInvoicePaymentReceiptYSolicitudPendiente() {
        Student student = persistStudent("81000001");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        ProgramEnrollmentApplication application =
                requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante()));

        assertThat(application.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(application.getPaymentReceipt()).isNotNull();
        assertThat(application.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(application.getPaymentReceipt().getInvoice().getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void solicitarConPagoDosVecesMientrasLaPrimeraSiguePendienteFalla() {
        Student student = persistStudent("81000002");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante()));

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante())))
                .isInstanceOf(AlreadyHasPendingApplicationException.class);
    }

    @Test
    void solicitarConPagoSinPriceRevisionConfiguradaFalla() {
        Student student = persistStudent("81000003");
        Program program = persistPrograma("Sin Precio " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        entityManager.flush();

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante())))
                .isInstanceOf(PriceRevisionNotFoundException.class);
    }

    @Test
    void solicitarConPagoFueraDeLaVentanaDeAdmisionFallaSinGenerarInvoiceNiComprobante() {
        Student student = persistStudent("81000005");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        // Ventana ya cerrada hace una semana — nadie debería poder pagar matrícula de una
        // cohorte con inscripciones cerradas, ni aunque la carrera tenga PriceRevision configurada.
        ProgramCohort cohort = persistCohort(program, LocalDate.now().minusDays(30), LocalDate.now().minusDays(7), director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        // Cuenta contra el total en vez de asumir la tabla vacía — otros tests de la suite
        // (Fase 6.1, no @Transactional) commitean Invoice reales en la misma base compartida.
        long invoicesAntes = invoiceRepository.count();

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante())))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);

        assertThat(invoiceRepository.count()).isEqualTo(invoicesAntes);
    }

    @Test
    void aprobarLaSolicitudApruebaTambienElComprobanteYPasaLaInvoiceAPaid() {
        Student student = persistStudent("81000004");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();
        ProgramEnrollmentApplication application =
                requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId(), comprobante()));

        Person preceptor = persistPersonBase("PREC" + System.nanoTime());
        Role rolPreceptor = roleRepository.findByName("PRECEPTOR").orElseThrow();
        preceptor.getRoles().add(rolPreceptor);
        entityManager.flush();

        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));

        entityManager.flush();
        entityManager.clear();

        ProgramEnrollmentApplication aprobada = entityManager.find(ProgramEnrollmentApplication.class, application.getId());
        assertThat(aprobada.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(aprobada.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.APPROVED);

        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED)))
                .isNotEmpty();

        assertThat(aprobada.getPaymentReceipt().getInvoice().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(aprobada.getPaymentReceipt().getInvoice().getBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        Student estudianteActualizado = entityManager.find(Student.class, student.getId());
        assertThat(estudianteActualizado.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");
    }
}
