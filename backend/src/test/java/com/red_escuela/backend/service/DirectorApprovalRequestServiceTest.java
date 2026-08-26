package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.DirectorApprovalRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardStatus;
import com.red_escuela.backend.entity.ExceptionType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.exception.InvalidDirectorApprovalRequestStateException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-DI-03: flujo de solicitud/aprobación/rechazo de excepciones del Director. Decisión 12:
 * esta fase NO implementa el efecto real de destrabar la validación correspondiente — se
 * verifica explícitamente que aprobar una excepción no tiene ningún efecto cruzado sobre
 * ExamBoardRegistrationService (deuda técnica documentada, no cableada).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectorApprovalRequestServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DirectorApprovalRequestService directorApprovalRequestService;

    @Autowired
    private ExamBoardRegistrationService examBoardRegistrationService;

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

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
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

    private void autenticarComoPersona(String dni) {
        Person persona = persistPersonBase(dni);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persona.getId().toString(), null));
    }

    @Test
    void decidirUnaSolicitudYaDecididaFalla() {
        Student student = persistStudent("A0000001");
        Program program = persistProgram("CarreraExcepcion1");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();
        autenticarComoPersona("PRECEPTOR_A0000001");

        DirectorApprovalRequest request = directorApprovalRequestService.solicitar(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), null, "Justificación de prueba"));

        autenticarComoPersona("DIRECTOR_A0000001");
        directorApprovalRequestService.aprobar(request.getId(), "OK");

        assertThatThrownBy(() -> directorApprovalRequestService.rechazar(request.getId(), "tarde"))
                .isInstanceOf(InvalidDirectorApprovalRequestStateException.class);
    }

    @Test
    void aprobarUnaExcepcionLateExamNoDestrabaUnaInscripcionAMesaFueraDeVentana() {
        Student student = persistStudent("A0000002");
        Program program = persistProgram("CarreraExcepcion2");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);

        Subject subject = new Subject();
        subject.setName("MateriaExcepcion" + System.nanoTime());
        entityManager.persist(subject);
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
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
        sse.setStatus(EnrollmentStatus.REGULAR);
        sse.setEnrolledAt(LocalDateTime.now());
        sse.setAttemptNumber(1);
        entityManager.persist(sse);

        // Mesa con la ventana de inscripción ya cerrada.
        ExamBoard examBoard = new ExamBoard();
        examBoard.setSubject(subject);
        examBoard.setTerm(term);
        examBoard.setExamDate(LocalDateTime.now().plusDays(1));
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        examBoard.setRegistrationStart(LocalDateTime.now().minusDays(10));
        examBoard.setRegistrationEnd(LocalDateTime.now().minusDays(1));
        entityManager.persist(examBoard);
        entityManager.flush();

        autenticarComoPersona("PRECEPTOR_A0000002");
        DirectorApprovalRequest request = directorApprovalRequestService.solicitar(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Llegó tarde por paro de transporte"));

        autenticarComoPersona("DIRECTOR_A0000002");
        DirectorApprovalRequest aprobada = directorApprovalRequestService.aprobar(request.getId(), "Aprobado");
        assertThat(aprobada.getStatus()).isEqualTo(DirectorApprovalRequestStatus.APPROVED);

        // Decisión 12: sin efecto cruzado — la mesa sigue rechazando fuera de ventana igual.
        assertThatThrownBy(() -> examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), sse.getId()))
                .isInstanceOf(ExamBoardRegistrationWindowClosedException.class);
    }
}
