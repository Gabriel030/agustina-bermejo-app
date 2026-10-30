package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalSubjectRequiredException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.service.DirectorApprovalRequestDecisionRecorder;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
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
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.enrollment.exception.InvalidDirectorApprovalRequestStateException;
import com.red_escuela.backend.evaluation.service.ExamBoardRegistrationService;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectorApprovalRequestUseCaseTest extends AbstractIntegrationTest {
    @Autowired
    private RequestDirectorApprovalUseCase requestDirectorApprovalUseCase;

    @Autowired
    private ApproveDirectorApprovalRequestUseCase approveDirectorApprovalRequestUseCase;

    @Autowired
    private RejectDirectorApprovalRequestUseCase rejectDirectorApprovalRequestUseCase;

    @Autowired
    private ExamBoardRegistrationService examBoardRegistrationService;

    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DirectorApprovalRequestDecisionRecorder decisionRecorder;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
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
        return persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
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

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name + System.nanoTime());
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject persistProgramSubject(Program program, Subject subject, CurricularTerm curricularTerm) {
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(0);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(curricularTerm);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return programSubject;
    }

    private Subject persistSubjectInPlan(Program program, String name) {
        Subject subject = persistSubject(name);
        persistProgramSubject(program, subject, CurricularTerm.FIRST);
        return subject;
    }

    private void persistPrerequisite(ProgramSubject programSubject, ProgramSubject prerequisite) {
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(programSubject);
        link.setPrerequisiteProgramSubject(prerequisite);
        link.setRequirementType(RequirementType.APPROVED);
        entityManager.persist(link);
    }

    private Term persistOpenTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-2C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(LocalDateTime.of(2026, 1, 1, 0, 0));
        term.setEnrollmentEnd(LocalDateTime.of(2099, 12, 31, 0, 0));
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSection(Subject subject, Term term) {
        SubjectSection section = new SubjectSection();
        section.setName("Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private SubjectSectionEnrollment persistRegularSectionEnrollment(Student student, ProgramEnrollment enrollment, Subject subject, Term term) {
        SubjectSectionEnrollment sse = new SubjectSectionEnrollment();
        sse.setStudent(student);
        sse.setProgramEnrollment(enrollment);
        sse.setSubjectSection(persistSection(subject, term));
        sse.setStatus(EnrollmentStatus.REGULAR);
        sse.setEnrolledAt(LocalDateTime.now());
        sse.setAttemptNumber(1);
        entityManager.persist(sse);
        return sse;
    }

    private ExamBoard persistExamBoardWithExpiredRegistrationWindow(Subject subject, Term term) {
        ExamBoard examBoard = new ExamBoard();
        examBoard.setSubject(subject);
        examBoard.setTerm(term);
        examBoard.setExamDate(LocalDateTime.now().plusDays(1));
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        examBoard.setRegistrationStart(LocalDateTime.now().minusDays(10));
        examBoard.setRegistrationEnd(LocalDateTime.now().minusDays(1));
        entityManager.persist(examBoard);
        return examBoard;
    }

    private void authenticateAsPerson(String dni) {
        Person persona = persistPersonBase(dni);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(persona.getId().toString(), null));
    }

    @Test
    void decidingAnAlreadyDecidedRequestFails() {
        Student student = persistStudent("A0000001");
        Program program = persistProgram("CarreraExcepcion1");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion1");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000001");

        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Justificación de prueba"));

        authenticateAsPerson("DIRECTOR_A0000001");
        approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(request.getId(), "OK"));

        assertThatThrownBy(() -> rejectDirectorApprovalRequestUseCase.reject(new DecideDirectorApprovalRequestCommand(request.getId(), "tarde")))
                .isInstanceOf(InvalidDirectorApprovalRequestStateException.class);
    }

    @Test
    void recordingADecisionThatIsNeitherApprovalNorRejectionFails() {
        Student student = persistStudent("A0000012");
        Program program = persistProgram("CarreraExcepcion12");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion12");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000012");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Pendiente"));
        authenticateAsPerson("DIRECTOR_A0000012");

        assertThatThrownBy(() -> decisionRecorder.record(request, DirectorApprovalRequestStatus.REQUESTED, "No es una decisión"))
                .isInstanceOf(InvalidDirectorApprovalRequestStateException.class);
        assertThat(request.getDecidedBy()).isNull();
        assertThat(request.getDecisionNotes()).isNull();
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(student.getPerson().getId())).isEmpty();
    }

    @Test
    void approvedLateExamExceptionAllowsRegistrationInAnExamBoardOutsideTheWindow() {
        Student student = persistStudent("A0000002");
        Program program = persistProgram("CarreraExcepcion2");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion2");
        Term term = persistOpenTerm();
        SubjectSectionEnrollment sse = persistRegularSectionEnrollment(student, enrollment, subject, term);
        ExamBoard examBoard = persistExamBoardWithExpiredRegistrationWindow(subject, term);
        entityManager.flush();

        authenticateAsPerson("PRECEPTOR_A0000002");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Llegó tarde por paro de transporte"));

        authenticateAsPerson("DIRECTOR_A0000002");
        DirectorApprovalRequest approved = approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(request.getId(), "Aprobado"));
        assertThat(approved.getStatus()).isEqualTo(DirectorApprovalRequestStatus.APPROVED);

        ExamBoardRegistration registration = examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), sse.getId());
        assertThat(registration.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void pendingLateExamExceptionDoesNotUnlockRegistrationOutsideTheWindow() {
        Student student = persistStudent("A0000003");
        Program program = persistProgram("CarreraExcepcion3");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion3");
        Term term = persistOpenTerm();
        SubjectSectionEnrollment sse = persistRegularSectionEnrollment(student, enrollment, subject, term);
        ExamBoard examBoard = persistExamBoardWithExpiredRegistrationWindow(subject, term);
        entityManager.flush();

        authenticateAsPerson("PRECEPTOR_A0000003");
        requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Todavía sin decidir"));

        assertThatThrownBy(() -> examBoardRegistrationService.registerProgramEnrollment(examBoard.getId(), sse.getId()))
                .isInstanceOf(ExamBoardRegistrationWindowClosedException.class);
    }

    @Test
    void approvedPrerequisiteWaiverAllowsEnrollingWithoutThePrerequisite() {
        Student student = persistStudent("A0000004");
        Program program = persistProgram("CarreraExcepcion4");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        ProgramSubject prerequisite = persistProgramSubject(program, persistSubject("PracticaI"), CurricularTerm.FIRST);
        Subject subject = persistSubject("PracticaII");
        ProgramSubject target = persistProgramSubject(program, subject, CurricularTerm.SECOND);
        persistPrerequisite(target, prerequisite);
        SubjectSection section = persistSection(subject, persistOpenTerm());
        entityManager.flush();

        authenticateAsPerson("PRECEPTOR_A0000004");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.PREREQUISITE_WAIVER, enrollment.getId(), subject.getId(), "Equivalencia en trámite"));
        authenticateAsPerson("DIRECTOR_A0000004");
        approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(request.getId(), null));

        SubjectSectionEnrollment created = subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId()));
        assertThat(created.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void prerequisiteWaiverForAnotherSubjectDoesNotUnlockEnrollment() {
        Student student = persistStudent("A0000005");
        Program program = persistProgram("CarreraExcepcion5");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject otherSubject = persistSubjectInPlan(program, "OtraMateria");
        ProgramSubject prerequisite = persistProgramSubject(program, persistSubject("PracticaI"), CurricularTerm.FIRST);
        Subject subject = persistSubject("PracticaII");
        ProgramSubject target = persistProgramSubject(program, subject, CurricularTerm.SECOND);
        persistPrerequisite(target, prerequisite);
        SubjectSection section = persistSection(subject, persistOpenTerm());
        entityManager.flush();

        authenticateAsPerson("PRECEPTOR_A0000005");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.PREREQUISITE_WAIVER, enrollment.getId(), otherSubject.getId(), "Otra materia"));
        authenticateAsPerson("DIRECTOR_A0000005");
        approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(request.getId(), null));

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                enrollment.getId(), new CreateSectionEnrollmentRequest(section.getId())))
                .isInstanceOf(PrerequisiteNotMetException.class);
    }

    @Test
    void requestWithoutSubjectFails() {
        Student student = persistStudent("A0000006");
        Program program = persistProgram("CarreraExcepcion6");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000006");

        assertThatThrownBy(() -> requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.PREREQUISITE_WAIVER, enrollment.getId(), null, "Sin materia")))
                .isInstanceOf(DirectorApprovalSubjectRequiredException.class);
    }

    @Test
    void requestWithSubjectOutsideTheProgramPlanFails() {
        Student student = persistStudent("A0000007");
        Program program = persistProgram("CarreraExcepcion7");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject foreignSubject = persistSubjectInPlan(persistProgram("OtraCarrera7"), "MateriaAjena");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000007");

        assertThatThrownBy(() -> requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), foreignSubject.getId(), "Materia de otra carrera")))
                .isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void requestForWithdrawnProgramEnrollmentFails() {
        Student student = persistStudent("A0000008");
        Program program = persistProgram("CarreraExcepcion8");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.WITHDRAWN);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion8");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000008");

        assertThatThrownBy(() -> requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Dado de baja")))
                .isInstanceOf(ProgramEnrollmentRequiredException.class);
    }

    @Test
    void requestForCompletedProgramEnrollmentFails() {
        Student student = persistStudent("A0000009");
        Program program = persistProgram("CarreraExcepcion9");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.COMPLETED);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion9");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000009");

        assertThatThrownBy(() -> requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Ya egresado")))
                .isInstanceOf(ProgramEnrollmentRequiredException.class);
    }

    @Test
    void approvingNotifiesTheStudentWithTheDecisionNotesInsteadOfTheJustification() {
        Student student = persistStudent("A0000010");
        Program program = persistProgram("CarreraExcepcion10");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion10");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000010");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.LATE_EXAM, enrollment.getId(), subject.getId(), "Justificación interna"));

        authenticateAsPerson("DIRECTOR_A0000010");
        approveDirectorApprovalRequestUseCase.approve(new DecideDirectorApprovalRequestCommand(request.getId(), "Por única vez"));

        List<Notification> received = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(student.getPerson().getId());
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getType()).isEqualTo(NotificationType.DIRECTOR_APPROVAL_REQUEST_APPROVED);
        assertThat(received.get(0).getMessage())
                .contains("Por única vez")
                .contains(subject.getName())
                .doesNotContain("Justificación interna");
    }

    @Test
    void rejectingNotifiesTheStudentWithTheDecisionNotes() {
        Student student = persistStudent("A0000011");
        Program program = persistProgram("CarreraExcepcion11");
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program);
        Subject subject = persistSubjectInPlan(program, "MateriaExcepcion11");
        entityManager.flush();
        authenticateAsPerson("PRECEPTOR_A0000011");
        DirectorApprovalRequest request = requestDirectorApprovalUseCase.request(
                new CreateDirectorApprovalRequestRequest(ExceptionType.PREREQUISITE_WAIVER, enrollment.getId(), subject.getId(), "Justificación interna"));

        authenticateAsPerson("DIRECTOR_A0000011");
        rejectDirectorApprovalRequestUseCase.reject(new DecideDirectorApprovalRequestCommand(request.getId(), "Falta el certificado"));

        List<Notification> received = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(student.getPerson().getId());
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getType()).isEqualTo(NotificationType.DIRECTOR_APPROVAL_REQUEST_REJECTED);
        assertThat(received.get(0).getMessage())
                .contains("Falta el certificado")
                .doesNotContain("Justificación interna");
    }
}
