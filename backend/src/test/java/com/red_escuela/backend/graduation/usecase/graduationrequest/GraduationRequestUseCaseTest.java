package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-AL-15/RF-DI-05: flujo de egreso con estados explícitos (decisión 8) — reusa
 * ProgramEnrollmentService.calcularProgreso sin tocarlo (decisión implícita de no duplicar), y
 * decisión 10: una deuda pendiente NO rechaza el trámite, lo deja en REQUESTED.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GraduationRequestUseCaseTest extends AbstractIntegrationTest {

    @Autowired
    private RequestGraduationUseCase requestGraduationUseCase;

    @Autowired
    private VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase;

    @Autowired
    private ApproveGraduationRequestUseCase approveGraduationRequestUseCase;

    @Autowired
    private RejectGraduationRequestUseCase rejectGraduationRequestUseCase;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

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

    // totalCreditsRequired = 0 y sin ProgramSubject: elegibleParaCompletar da true trivialmente
    // (sin obligatorias pendientes, créditos acumulados >= 0 requeridos, sin grupos electivos).
    private Program persistProgramSinRequisitos(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private Program persistProgramConMateriaPendiente(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(10);
        entityManager.persist(program);

        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName("Materia pendiente " + name);
        entityManager.persist(subject);

        ProgramSubject ps = new ProgramSubject();
        ps.setProgram(program);
        ps.setSubject(subject);
        ps.setCredits(10);
        ps.setCurricularYear(1);
        ps.setCurricularTerm(CurricularTerm.FIRST);
        ps.setStatus(StatusType.ACTIVE);
        entityManager.persist(ps);
        return program;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistInvoice(ProgramEnrollment enrollment, LocalDate dueDate) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(java.time.YearMonth.from(dueDate));
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(BigDecimal.valueOf(100));
        invoice.setStatus(InvoiceStatus.PENDING);
        entityManager.persist(invoice);
    }

    private void autenticarComoPersona(String dni) {
        Person persona = persistPersonBase(dni);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persona.getId().toString(), null));
    }

    @Test
    void solicitarSinSerElegibleFalla() {
        Student student = persistStudent("80000001");
        Program program = persistProgramConMateriaPendiente("CarreraConPendientes");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        assertThatThrownBy(() -> requestGraduationUseCase.request(enrollment.getId()))
                .isInstanceOf(NotEligibleForGraduationException.class);
    }

    @Test
    void solicitarDosVecesSinQueLaPrimeraSeRechaceFalla() {
        Student student = persistStudent("80000002");
        Program program = persistProgramSinRequisitos("CarreraSinRequisitos1");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        requestGraduationUseCase.request(enrollment.getId());

        assertThatThrownBy(() -> requestGraduationUseCase.request(enrollment.getId()))
                .isInstanceOf(GraduationRequestAlreadyActiveException.class);
    }

    @Test
    void verificarDeudaConFacturasPendientesFallaYElTramiteSigueEnRequested() {
        Student student = persistStudent("80000003");
        Program program = persistProgramSinRequisitos("CarreraSinRequisitos2");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        persistInvoice(enrollment, LocalDate.now().plusDays(30));
        entityManager.flush();

        assertThatThrownBy(() -> verifyGraduationRequestDebtUseCase.verify(request.getId()))
                .isInstanceOf(PendingDebtException.class);

        GraduationRequest recargado = entityManager.find(GraduationRequest.class, request.getId());
        assertThat(recargado.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
    }

    @Test
    void aprobarElEgresoCompletaLaCarreraEnLaMismaTransaccion() {
        Student student = persistStudent("80000004");
        Program program = persistProgramSinRequisitos("CarreraSinRequisitos3");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        GraduationRequest request = requestGraduationUseCase.request(enrollment.getId());
        autenticarComoPersona("ADMIN80000004");
        verifyGraduationRequestDebtUseCase.verify(request.getId());
        autenticarComoPersona("DIRECTOR80000004");
        approveGraduationRequestUseCase.approve(request.getId());

        GraduationRequest recargado = entityManager.find(GraduationRequest.class, request.getId());
        assertThat(recargado.getStatus()).isEqualTo(GraduationRequestStatus.APPROVED);
        ProgramEnrollment enrollmentRecargado = entityManager.find(ProgramEnrollment.class, enrollment.getId());
        assertThat(enrollmentRecargado.getStatus()).isEqualTo(ProgramEnrollmentStatus.COMPLETED);
        assertThat(enrollmentRecargado.getCompletionDate()).isNotNull();
    }

    @Test
    void rechazarUnTramiteRequestedPermiteSolicitarDeNuevo() {
        Student student = persistStudent("80000005");
        Program program = persistProgramSinRequisitos("CarreraSinRequisitos4");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();

        GraduationRequest primero = requestGraduationUseCase.request(enrollment.getId());
        autenticarComoPersona("ADMIN80000005");
        rejectGraduationRequestUseCase.reject(new RejectGraduationRequestCommand(primero.getId(), "Datos incompletos"));

        GraduationRequest recargado = entityManager.find(GraduationRequest.class, primero.getId());
        assertThat(recargado.getStatus()).isEqualTo(GraduationRequestStatus.REJECTED);

        GraduationRequest segundo = requestGraduationUseCase.request(enrollment.getId());
        assertThat(segundo.getId()).isNotEqualTo(primero.getId());
        assertThat(segundo.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
    }
}
