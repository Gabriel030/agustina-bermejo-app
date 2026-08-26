package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.ChangeSectionRequest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.entity.TermEnrollmentOverride;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.exception.TermEnrollmentOverrideClosedException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de regresión de la auditoría de integridad post-PR #7: hallazgos que necesitan
 * verificar el TIPO exacto de excepción de dominio, no solo el status HTTP (los handlers
 * de GlobalExceptionHandler no devuelven body, así que un test de controller no puede
 * distinguir entre dos excepciones que mapean al mismo status).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectSectionEnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(String dni, StatusType personStatus, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(personStatus);
        entityManager.persist(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject persistProgramSubject(Program program, Subject subject) {
        ProgramSubject ps = new ProgramSubject();
        ps.setProgram(program);
        ps.setSubject(subject);
        ps.setCredits(10);
        ps.setCurricularYear(1);
        ps.setCurricularTerm(CurricularTerm.FIRST);
        ps.setStatus(StatusType.ACTIVE);
        entityManager.persist(ps);
        return ps;
    }

    private Term persistTerm(LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(enrollmentStart);
        term.setEnrollmentEnd(enrollmentEnd);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSubjectSection(Subject subject, Term term, Integer capacity) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
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

    @Test
    void inscribirConVentanaDeInscripcionInvertidaLanzaExcepcionDeConfiguracionNoDeVentanaCerrada() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000001", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaVentanaInvertida");
        Program program = persistProgram("CarreraVentanaInvertida");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        // Ventana invertida: start posterior a end — error de carga de datos, no "está cerrada".
        Term term = persistTerm(now.plusDays(10), now.minusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(InvalidWindowConfigurationException.class);
    }

    @Test
    void inscribirAUnaSeccionCuyaMateriaNoEstaEnElPlanDeLaCarreraLanzaExcepcionEspecifica() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000002", StatusType.ACTIVE, institution);
        Subject subjectFueraDePlan = persistSubject("MateriaFueraDePlan");
        Program program = persistProgram("CarreraSinEsaMateria");
        // Nota: NO se crea ProgramSubject para (program, subjectFueraDePlan) — combinación
        // inconsistente de programEnrollmentId + sectionId.
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subjectFueraDePlan, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void reinscribirseSinPasarPorCambiarComisionAOtraSeccionDelMismoTermNoIncrementaAttemptNumber() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000003", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaReinscripcionManual");
        Program program = persistProgram("CarreraReinscripcionManual");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection seccionA = persistSubjectSection(subject, term, null);
        SubjectSection seccionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment primera = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionA.getId()));
        assertThat(primera.getAttemptNumber()).isEqualTo(1);

        subjectSectionEnrollmentService.darDeBaja(primera.getId());

        // Reinscripción MANUAL (no change-section) a otra comisión de la MISMA materia,
        // mismo Term: por decisión 8, esto no es un intento nuevo.
        SubjectSectionEnrollment segunda = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionB.getId()));
        assertThat(segunda.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void reinscribirseAOtroTermTrasBajaSiIncrementaAttemptNumber() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000004", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOtroTerm");
        Program program = persistProgram("CarreraOtroTerm");
        persistProgramSubject(program, subject);
        Term term1 = persistTerm(null, null);
        Term term2 = persistTerm(null, null);
        SubjectSection seccionTerm1 = persistSubjectSection(subject, term1, null);
        SubjectSection seccionTerm2 = persistSubjectSection(subject, term2, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment primera = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionTerm1.getId()));
        subjectSectionEnrollmentService.darDeBaja(primera.getId());

        SubjectSectionEnrollment segunda = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionTerm2.getId()));
        assertThat(segunda.getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void cambiarComisionConAlumnoInactivoFalla() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000005", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaAlumnoInactivo");
        Program program = persistProgram("CarreraAlumnoInactivo");
        persistProgramSubject(program, subject);
        Term term = persistTerm(null, null);
        SubjectSection seccionA = persistSubjectSection(subject, term, null);
        SubjectSection seccionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment original = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(seccionA.getId()));

        student.getPerson().setStatus(StatusType.DELETED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.cambiarComision(
                original.getId(), new ChangeSectionRequest(seccionB.getId())))
                .isInstanceOf(StudentNotActiveException.class);
    }

    // Decisión 5 de la Spec Procesos administrativos: override manual con prioridad sobre la
    // validación por fecha ya existente, sin tocarla ni duplicarla.
    @Test
    void cerrarManualmenteLaVentanaRechazaLaInscripcionAunqueLaFechaSigaVigente() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000006", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOverrideCerrado");
        Program program = persistProgram("CarreraOverrideCerrado");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(1), now.plusDays(10));
        term.setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(TermEnrollmentOverrideClosedException.class);
    }

    @Test
    void abrirManualmenteLaVentanaPermiteInscribirseAunqueLaFechaYaHayaVencido() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000007", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaOverrideAbierto");
        Program program = persistProgram("CarreraOverrideAbierto");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        Term term = persistTerm(now.minusDays(20), now.minusDays(10));
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        SubjectSectionEnrollment enrollmentResult = subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        assertThat(enrollmentResult.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void sinOverrideSigueRigiendoSePorFechaComoAntes() {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000008", StatusType.ACTIVE, institution);
        Subject subject = persistSubject("MateriaSinOverride");
        Program program = persistProgram("CarreraSinOverride");
        persistProgramSubject(program, subject);
        LocalDateTime now = LocalDateTime.now();
        // Sin override (null, default) y fecha ya vencida: tiene que seguir rechazando por
        // fecha, no por override (comportamiento previo a esta fase, sin cambios).
        Term term = persistTerm(now.minusDays(20), now.minusDays(10));
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.inscribir(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(com.red_escuela.backend.exception.EnrollmentWindowClosedException.class);
    }
}
