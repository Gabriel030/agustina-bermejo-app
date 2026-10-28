package com.red_escuela.backend.evaluation.service;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.evaluation.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.evaluation.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.identity.exception.StudentNotRegularException;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.evaluation.exception.StudentFreeByAbsencesException;
import java.time.LocalDate;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
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
        subject.setCode(TestSubjectCodes.next());
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
    void registeringWithoutBeingRegularFails() {
        Student student = persistStudent("90000001");
        Subject subject = persistSubject("MateriaNoRegular", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraNoRegular");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(StudentNotRegularException.class);
    }

    @Test
    void registeringInExamBoardOfAnotherSubjectFails() {
        Student student = persistStudent("90000002");
        Subject subject = persistSubject("MateriaCursada", null);
        Subject otherSubject = persistSubject("MateriaDeLaMesa", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(otherSubject, term);
        Program program = persistProgram("CarreraMesaMismatch");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(ExamBoardSubjectMismatchException.class);
    }

    @Test
    void registerWithdrawAndReRegisterReactivatesRowWithoutDuplicating() {
        Student student = persistStudent("90000003");
        Subject subject = persistSubject("MateriaReinscripcionMesa", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraReinscripcionMesa");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration first = examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId());
        examBoardRegistrationService.withdraw(first.getId());

        ExamBoardRegistration second = examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId());
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);

        List<ExamBoardRegistration> all = examBoardRegistrationRepository.findByExamBoardId(examBoard.getId());
        assertThat(all).hasSize(1);
    }

    @Test
    void registeringTwiceWithoutWithdrawingFails() {
        Student student = persistStudent("90000004");
        Subject subject = persistSubject("MateriaDobleInscripcion", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraDobleInscripcion");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId());

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(AlreadyRegisteredInExamBoardException.class);
    }

    @Test
    void reachingConfiguredAttemptLimitBlocksNewRegistration() {
        Student student = persistStudent("90000005");
        Subject subject = persistSubject("MateriaConTope", 1);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard1 = persistExamBoard(subject, term);
        ExamBoard examBoard2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraConTope");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration taken = examBoardRegistrationService.registerProgramEnrollment(examBoard1.getId(), enrollment.getId());
        taken.setStatus(ExamBoardRegistrationStatus.GRADED);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard2.getId(), enrollment.getId()))
                .isInstanceOf(MaxExamAttemptsExceededException.class);
    }

    @Test
    void aWithdrawnAttemptDoesNotConsumeTheAttemptLimit() {
        Student student = persistStudent("90000006");
        Subject subject = persistSubject("MateriaTopeConBaja", 1);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard examBoard1 = persistExamBoard(subject, term);
        ExamBoard examBoard2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraTopeConBaja");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration first = examBoardRegistrationService.registerProgramEnrollment(examBoard1.getId(), enrollment.getId());
        examBoardRegistrationService.withdraw(first.getId());

        ExamBoardRegistration second = examBoardRegistrationService.registerProgramEnrollment(examBoard2.getId(), enrollment.getId());
        assertThat(second.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void registeringInAnotherCallOfSameSubjectWithOneActiveFails() {
        Student student = persistStudent("90000007");
        Subject subject = persistSubject("MateriaDosLlamadosDisponibles", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard call1 = persistExamBoard(subject, term);
        ExamBoard call2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraDosLlamadosDisponibles");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        examBoardRegistrationService.registerProgramEnrollment(call1.getId(), enrollment.getId());

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(call2.getId(), enrollment.getId()))
                .isInstanceOf(AlreadyRegisteredInAnotherExamBoardException.class);
    }

    @Test
    void withdrawingFromOneCallEnablesRegisteringInAnotherOfSameSubject() {
        Student student = persistStudent("90000008");
        Subject subject = persistSubject("MateriaCambioDeLlamado", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard call1 = persistExamBoard(subject, term);
        ExamBoard call2 = persistExamBoard(subject, term);
        Program program = persistProgram("CarreraCambioDeLlamado");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration first = examBoardRegistrationService.registerProgramEnrollment(call1.getId(), enrollment.getId());
        examBoardRegistrationService.withdraw(first.getId());

        ExamBoardRegistration second = examBoardRegistrationService.registerProgramEnrollment(call2.getId(), enrollment.getId());
        assertThat(second.getExamBoard().getId()).isEqualTo(call2.getId());
        assertThat(second.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void byDefaultTheRegularFinalHasASingleAttempt() {
        Student student = persistStudent("90000201");
        Subject subject = persistSubject("MateriaUnSoloFinal", null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        ExamBoard firstCall = persistExamBoard(subject, term);
        ExamBoard secondCall = persistExamBoard(subject, term);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("CarreraUnSoloFinal"));
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        entityManager.flush();

        ExamBoardRegistration taken = examBoardRegistrationService.registerProgramEnrollment(firstCall.getId(), enrollment.getId());
        taken.setStatus(ExamBoardRegistrationStatus.GRADED);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(secondCall.getId(), enrollment.getId()))
                .isInstanceOf(MaxExamAttemptsExceededException.class);
    }

    @Test
    void retakingTheSubjectGrantsANewRegularFinalAttempt() {
        Student student = persistStudent("90000202");
        Subject subject = persistSubject("MateriaRecursada", null);
        Term term = persistTerm();
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("CarreraRecursada"));
        SubjectSectionEnrollment firstTake = persistEnrollment(student, programEnrollment, persistSection(subject, term), EnrollmentStatus.REGULAR);
        ExamBoard failedCall = persistExamBoard(subject, term);
        entityManager.flush();

        ExamBoardRegistration failed = examBoardRegistrationService.registerProgramEnrollment(failedCall.getId(), firstTake.getId());
        failed.setStatus(ExamBoardRegistrationStatus.GRADED);
        firstTake.setStatus(EnrollmentStatus.FAILED);
        SubjectSectionEnrollment retake = persistEnrollment(student, programEnrollment, persistSection(subject, term), EnrollmentStatus.REGULAR);
        retake.setAttemptNumber(2);
        ExamBoard nextCall = persistExamBoard(subject, term);
        entityManager.flush();

        ExamBoardRegistration registration = examBoardRegistrationService.registerProgramEnrollment(nextCall.getId(), retake.getId());

        assertThat(registration.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    private SubjectSectionEnrollment regularEnrollmentWithAttendance(String dni, AttendanceStatus... statuses) {
        Student student = persistStudent(dni);
        Subject subject = persistSubject("Materia asistencia " + dni, null);
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("Carrera asistencia " + dni));
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section, EnrollmentStatus.REGULAR);
        Person teacher = persistStudent("D" + dni).getPerson();
        Instructor instructor = new Instructor();
        instructor.setPerson(teacher);
        entityManager.persist(instructor);
        for (int i = 0; i < statuses.length; i++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(LocalDate.of(2025, 9, 1 + i));
            attendance.setStatus(statuses[i]);
            attendance.setRecordedBy(instructor);
            entityManager.persist(attendance);
        }
        return enrollment;
    }

    @Test
    void aRegularStudentOverTheAbsenceLimitCannotRegisterForTheFinal() {
        SubjectSectionEnrollment enrollment = regularEnrollmentWithAttendance("90000101",
                AttendanceStatus.ABSENT, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);
        ExamBoard examBoard = persistExamBoard(enrollment.getSubjectSection().getSubject(), enrollment.getSubjectSection().getTerm());
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId()))
                .isInstanceOf(StudentFreeByAbsencesException.class);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void onceTheAbsencesAreJustifiedBelowTheLimitTheStudentCanRegisterForTheFinal() {
        SubjectSectionEnrollment enrollment = regularEnrollmentWithAttendance("90000102",
                AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFIED, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT);
        ExamBoard examBoard = persistExamBoard(enrollment.getSubjectSection().getSubject(), enrollment.getSubjectSection().getTerm());
        entityManager.flush();

        ExamBoardRegistration registration = examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), enrollment.getId());

        assertThat(registration.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }
}
