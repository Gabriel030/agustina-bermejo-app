package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Payment;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.exception.FeeScheduleNotFoundException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.PaymentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.RoleRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Épica 7 Fase 3: solicitud de alta en carrera con pago de matrícula bloqueante
 * (Invoice+PaymentReceipt en la misma request que solicitar) y aprobación conjunta
 * de la solicitud y el comprobante en una sola transacción.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramEnrollmentApplicationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramEnrollmentApplicationService applicationService;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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

    private ProgramCohort persistCohort(Program program, LocalDateTime admissionStart, LocalDateTime admissionEnd) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setProgram(program);
        cohort.setOrdenEnAnio(1);
        cohort.setAdmissionStart(admissionStart);
        cohort.setAdmissionEnd(admissionEnd);
        cohort.setCursadaStart(LocalDate.now());
        entityManager.persist(cohort);
        return cohort;
    }

    private void persistFeeSchedule(Program program, BigDecimal amount, Person createdBy) {
        com.red_escuela.backend.entity.FeeSchedule feeSchedule = new com.red_escuela.backend.entity.FeeSchedule();
        feeSchedule.setProgram(program);
        feeSchedule.setConcept(FeeScheduleConcept.MATRICULA);
        feeSchedule.setAmount(amount);
        feeSchedule.setVigenteDesde(LocalDate.now().minusDays(1));
        feeSchedule.setCreatedBy(createdBy);
        feeSchedule.setCreatedAt(LocalDateTime.now());
        entityManager.persist(feeSchedule);
    }

    private MultipartFile comprobante() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void requestWithPaymentCreatesInvoicePaymentReceiptAndRequestPending() {
        Student student = persistStudent("81000001");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        ProgramEnrollmentApplication application =
                applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante());

        assertThat(application.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(application.getPaymentReceipt()).isNotNull();
        assertThat(application.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(application.getPaymentReceipt().getInvoice().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void requestWithPaymentTwoTimesWhileTheFirstRemainsPendingFails() {
        Student student = persistStudent("81000002");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante());

        assertThatThrownBy(() -> applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante()))
                .isInstanceOf(AlreadyHasPendingApplicationException.class);
    }

    @Test
    void requestWithPaymentWithoutFeeScheduleConfiguredFails() {
        Student student = persistStudent("81000003");
        Program program = persistPrograma("Sin Precio " + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null);
        entityManager.flush();

        assertThatThrownBy(() -> applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante()))
                .isInstanceOf(FeeScheduleNotFoundException.class);
    }

    @Test
    void requestWithPaymentOutsideOfTheWindowOfAdmissionFailsWithoutGenerateInvoiceNorReceipt() {
        Student student = persistStudent("81000005");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        // Ventana ya cerrada hace una semana — nadie debería poder pagar matrícula de una
        // cohorte con inscripciones cerradas, ni aunque la carrera tenga FeeSchedule configurado.
        ProgramCohort cohort = persistCohort(program, LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(7));
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        assertThatThrownBy(() -> applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante()))
                .isInstanceOf(AdmissionWindowClosedException.class);

        assertThat(invoiceRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void approveTheRequestApprovesAlsoTheReceiptAndCreatesThePaymentReal() {
        Student student = persistStudent("81000004");
        Program program = persistPrograma("Diseño Gráfico " + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null);
        Person director = persistPersonBase("DIR" + System.nanoTime());
        persistFeeSchedule(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();
        ProgramEnrollmentApplication application =
                applicationService.solicitarConPago(student, program.getId(), cohort.getId(), comprobante());

        Person preceptor = persistPersonBase("PREC" + System.nanoTime());
        Role rolPreceptor = roleRepository.findByName("PRECEPTOR").orElseThrow();
        preceptor.getRoles().add(rolPreceptor);
        entityManager.flush();

        applicationService.approve(application.getId(), preceptor);

        entityManager.flush();
        entityManager.clear();

        ProgramEnrollmentApplication aprobada = entityManager.find(ProgramEnrollmentApplication.class, application.getId());
        assertThat(aprobada.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(aprobada.getPaymentReceipt().getStatus()).isEqualTo(PaymentReceiptStatus.APPROVED);

        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED)))
                .isNotEmpty();

        List<Payment> pagos = paymentRepository.findByInvoiceId(aprobada.getPaymentReceipt().getInvoice().getId());
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));

        Student estudianteActualizado = entityManager.find(Student.class, student.getId());
        assertThat(estudianteActualizado.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");
    }
}
