package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.entity.ExamBoardStatus;
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
import com.red_escuela.backend.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.exception.StudentNotRegularException;
import com.red_escuela.backend.repository.ExamBoardRegistrationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-AL-05/06: inscripción a mesa de examen con regularidad scopeada por carrera (decisión 8),
 * reactivación post-baja sin chocar con el índice único parcial (mismo criterio que
 * SubjectSectionEnrollmentService.inscribir), y tope de reintentos configurable (decisión de
 * producto: Subject.maxFinalExamAttempts).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamBoardRegistrationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ExamBoardRegistrationService examBoardRegistrationService;

    @Autowired
    private ExamBoardRegistrationRepository examBoardRegistrationRepository;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(String dni) {
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

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Subject persistSubject(String name, Integer maxFinalExamAttempts) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setMaxFinalExamAttempts(maxFinalExamAttempts);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSection(Subject subject, Term term) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private ExamBoard persistExamBoard(Subject subject, Term term) {
        ExamBoard examBoard = new ExamBoard();
        examBoard.setSubject(subject);
        examBoard.setTerm(term);
        examBoard.setExamDate(LocalDateTime.now().plusDays(1));
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(examBoard);
        return examBoard;
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
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

    private SubjectSectionEnrollment persistEnrollment(
            Student student, ProgramEnrollment programEnrollment, SubjectSection section, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void inscribirseSinEstarRegularFalla() {
        Student student = persistStudent("90000001");
        Subject subject = persistSubject("MateriaNoRegular", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraNoRegular");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(StudentNotRegularException.class);
    }

    @Test
    void inscribirseAUnaMesaDeOtraMateriaFalla() {
        Student student = persistStudent("90000002");
        Subject subject = persistSubject("MateriaCursada", null);
        Subject otraSubject = persistSubject("MateriaDeLaMesa", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(otraSubject, term);
        Program program = persistProgram("CarreraMesaMismatch");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(ExamBoardSubjectMismatchException.class);
    }

    @Test
    void inscribirseYDarseDeBajaYReinscribirseReactivaLaFilaSinDuplicar() {
        Student student = persistStudent("90000003");
        Subject subject = persistSubject("MateriaReinscripcionMesa", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraReinscripcionMesa");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration primera = examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId());
        examBoardRegistrationService.darDeBaja(primera.getId());

        ExamBoardRegistration segunda = examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId());
        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(segunda.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);

        List<ExamBoardRegistration> todas = examBoardRegistrationRepository.findByExamBoardId(examBoard.getId());
        assertThat(todas).hasSize(1);
    }

    @Test
    void inscribirseDosVecesSinDarseDeBajaFalla() {
        Student student = persistStudent("90000004");
        Subject subject = persistSubject("MateriaDobleInscripcion", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraDobleInscripcion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId());

        assertThatThrownBy(() -> examBoardRegistrationService.inscribirProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(AlreadyRegisteredInExamBoardException.class);
    }

    @Test
    void alcanzarElTopeDeReintentosConfiguradoBloqueaUnaNuevaInscripcion() {
        Student student = persistStudent("90000005");
        Subject subject = persistSubject("MateriaConTope", 1);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard mesa1 = persistExamBoard(subject, term);
        ExamBoard mesa2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraConTope");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        // Primer intento: consume el único cupo permitido (maxFinalExamAttempts = 1).
        examBoardRegistrationService.inscribirProgramEnrollment(mesa1.getId(), enrollment.getId());

        assertThatThrownBy(() -> examBoardRegistrationService.inscribirProgramEnrollment(mesa2.getId(), enrollment.getId()))
                .isInstanceOf(MaxExamAttemptsExceededException.class);
    }

    @Test
    void unIntentoDadoDeBajaNoConsumeElTopeDeReintentos() {
        Student student = persistStudent("90000006");
        Subject subject = persistSubject("MateriaTopeConBaja", 1);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard mesa1 = persistExamBoard(subject, term);
        ExamBoard mesa2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraTopeConBaja");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration primera = examBoardRegistrationService.inscribirProgramEnrollment(mesa1.getId(), enrollment.getId());
        examBoardRegistrationService.darDeBaja(primera.getId());

        // El intento en mesa1 fue WITHDRAWN (no cuenta) — inscribirse a mesa2 tiene que poder.
        ExamBoardRegistration segunda = examBoardRegistrationService.inscribirProgramEnrollment(mesa2.getId(), enrollment.getId());
        assertThat(segunda.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }
}
