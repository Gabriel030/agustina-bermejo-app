package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CertificateIssuance;
import com.red_escuela.backend.entity.EnrollmentStatus;
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
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.CertificateNotEligibleException;
import com.red_escuela.backend.repository.CertificateIssuanceRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-AL-12: constancias sin entidad propia (decisión 6). REGULAR_STUDENT exige (ajuste de
 * producto sobre la spec original) ENROLLED + cursada activa + PaymentStanding AL_DIA — las
 * 3 condiciones se prueban por separado para aislar cuál falla en cada caso.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CertificateServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateIssuanceRepository certificateIssuanceRepository;

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
        person.setName("Alumno");
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

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private void persistCursadaActiva(Student student, ProgramEnrollment enrollment) {
        Subject subject = new Subject();
        subject.setCode(String.valueOf((int) (System.nanoTime() % 9000) + 1000));
        subject.setName("MateriaCursandoAhora" + System.nanoTime());
        entityManager.persist(subject);
        Term term = persistTerm();
        SubjectSection section = new SubjectSection();
        section.setName("Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);

        SubjectSectionEnrollment sse = new SubjectSectionEnrollment();
        sse.setStudent(student);
        sse.setProgramEnrollment(enrollment);
        sse.setSubjectSection(section);
        sse.setStatus(EnrollmentStatus.IN_PROGRESS);
        sse.setEnrolledAt(LocalDateTime.now());
        sse.setAttemptNumber(1);
        entityManager.persist(sse);
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
    void constanciaRegularSinCursadaActivaFalla() {
        Student student = persistStudent("90000001");
        Program program = persistProgram("CarreraSinCursadaActiva");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        autenticarComoPersona("ADMIN90000001");

        assertThatThrownBy(() -> certificateService.emitirConstanciaRegular(enrollment.getId()))
                .isInstanceOf(CertificateNotEligibleException.class);
    }

    @Test
    void constanciaRegularConCursadaActivaPeroMorosoFalla() {
        Student student = persistStudent("90000002");
        Program program = persistProgram("CarreraMoroso");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        persistCursadaActiva(student, enrollment);
        persistInvoice(enrollment, LocalDate.now().minusDays(5));
        entityManager.flush();
        autenticarComoPersona("ADMIN90000002");

        assertThatThrownBy(() -> certificateService.emitirConstanciaRegular(enrollment.getId()))
                .isInstanceOf(CertificateNotEligibleException.class);
    }

    @Test
    void constanciaRegularConLosTresRequisitosEmiteUnPdfValidoYRegistraElHistorial() {
        Student student = persistStudent("90000003");
        Program program = persistProgram("CarreraRegularCompleta");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        persistCursadaActiva(student, enrollment);
        // InvoiceQueryService.tieneDeuda solo cuenta como deuda una Invoice OVERDUE o PENDING
        // ya vencida — una cuota futura no vencida nunca cuenta como deuda.
        persistInvoice(enrollment, LocalDate.now().plusDays(30));
        entityManager.flush();
        autenticarComoPersona("ADMIN90000003");

        byte[] pdf = certificateService.emitirConstanciaRegular(enrollment.getId());
        assertThat(pdf).isNotEmpty();
        // Encabezado estándar de un archivo PDF válido.
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        List<CertificateIssuance> historial = certificateIssuanceRepository.findByProgramEnrollmentId(enrollment.getId());
        assertThat(historial).hasSize(1);
    }

    @Test
    void constanciaRegularDeUnaProgramEnrollmentNoEnrolledFalla() {
        Student student = persistStudent("90000004");
        Program program = persistProgram("CarreraDadaDeBaja");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.WITHDRAWN);
        entityManager.flush();
        autenticarComoPersona("ADMIN90000004");

        assertThatThrownBy(() -> certificateService.emitirConstanciaRegular(enrollment.getId()))
                .isInstanceOf(CertificateNotEligibleException.class);
    }

    @Test
    void constanciaDeMateriasAprobadasConCeroMateriasAprobadasEmiteUnPdfValidoSinExcepcion() {
        Student student = persistStudent("90000005");
        Program program = persistProgram("CarreraSinAprobadas");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        autenticarComoPersona("ADMIN90000005");

        byte[] pdf = certificateService.emitirConstanciaMateriasAprobadas(enrollment.getId());
        assertThat(pdf).isNotEmpty();
    }
}
