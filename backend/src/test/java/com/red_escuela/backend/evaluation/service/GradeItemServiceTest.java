package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
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
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.usecase.attendance.JustifyAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.attendance.JustifyAttendanceUseCase;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.red_escuela.backend.shared.domain.InvalidGradeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GradeItemServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private JustifyAttendanceUseCase justifyAttendanceUseCase;

    @Autowired
    private GradeItemService gradeItemService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EvaluationSchemeService evaluationSchemeService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(String dni, Institution institution) {
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
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Instructor persistInstructor(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Docente");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);

        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private EvaluationScheme persistScheme(int examCount, int tpCount, boolean hasFinal) {
        return persistScheme(examCount, tpCount, hasFinal, null);
    }

    private EvaluationScheme persistScheme(int examCount, int tpCount, boolean hasFinal, Double promotionMinGrade) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema de prueba");
        scheme.setExamCountExpected(examCount);
        scheme.setTpCountExpected(tpCount);
        scheme.setHasFinal(hasFinal);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        scheme.setPromotionMinGrade(promotionMinGrade == null ? null : Grade.of(BigDecimal.valueOf(promotionMinGrade)));
        entityManager.persist(scheme);
        return scheme;
    }

    private Subject persistSubject(String name) {
        return persistSubject(name, null);
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

    private SubjectSection persistSection(Subject subject, Term term, EvaluationScheme scheme) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private void assignInstructor(SubjectSection section, Instructor instructor) {
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
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

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private SubjectSectionEnrollment persistEnrollment(Student student, ProgramEnrollment programEnrollment, SubjectSection section) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void loadingGradeWithoutAssignedSchemeThrowsException() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000001", institution);
        Instructor instructor = persistInstructor("71000002");
        Subject subject = persistSubject("MateriaSinEsquema");
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term, null);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraSinEsquema");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        CreateGradeItemRequest request = new CreateGradeItemRequest(
                GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null);

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(), request))
                .isInstanceOf(NoEvaluationSchemeException.class);
    }

    @Test
    void instructorNotInSectionCannotLoadGrade() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000003", institution);
        Instructor outsideInstructor = persistInstructor("71000004");
        Subject subject = persistSubject("MateriaComisionAjena");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(1, 0, false);
        SubjectSection section = persistSection(subject, term, scheme);
        Program program = persistProgram("CarreraComisionAjena");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        CreateGradeItemRequest request = new CreateGradeItemRequest(
                GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null);

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), outsideInstructor.getId(), request))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void averageOfExamsBelowMinRegularLeavesEnrollmentFailed() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000005", institution);
        Instructor instructor = persistInstructor("71000006");
        Subject subject = persistSubject("MateriaPromedioBajo");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(2, 0, false);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraPromedioBajo");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(2.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(3.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void averageOfExamsAboveMinRegularLeavesEnrollmentRegular() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000007", institution);
        Instructor instructor = persistInstructor("71000008");
        Subject subject = persistSubject("MateriaPromedioAlto");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(2, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraPromedioAlto");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void loadingSameEvaluationTwiceCorrectsGradeInsteadOfDuplicating() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000009", institution);
        Instructor instructor = persistInstructor("71000010");
        Subject subject = persistSubject("MateriaCorreccionNota");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(1, 0, false);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraCorreccionNota");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(2.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(9.0), "corregida", null));

        var items = gradeItemService.listByProgramEnrollment(enrollment.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(9.0)));
    }

    @Test
    void finalGradeAboveApprovalMinimumApprovesEnrollmentAndSetsFinalScore() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000011", institution);
        Instructor instructor = persistInstructor("71000012");
        Subject subject = persistSubject("MateriaConFinal");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(0, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraConFinal");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(8.0)));
    }

    @Test
    void aRegularStudentWhoFailsTheOnlyRegularFinalFailsTheSubject() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000121", institution);
        Instructor instructor = persistInstructor("71000122");
        Subject subject = persistSubject("MateriaFinalUnico");
        SubjectSection section = persistSection(subject, persistTerm(), persistScheme(0, 0, true));
        assignInstructor(section, instructor);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, persistProgramEnrollment(student, persistProgram("CarreraFinalUnico")), section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(3.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(subject.getMaxFinalExamAttempts()).isEqualTo(1);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void aRegularStudentWhoPassesTheRegularFinalApprovesWithAFinalExam() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000123", institution);
        Instructor instructor = persistInstructor("71000124");
        Subject subject = persistSubject("MateriaFinalUnicoAprobado");
        SubjectSection section = persistSection(subject, persistTerm(), persistScheme(0, 0, true));
        assignInstructor(section, instructor);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, persistProgramEnrollment(student, persistProgram("CarreraFinalUnicoOk")), section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalGradeType()).isEqualTo(com.red_escuela.backend.evaluation.entity.FinalGradeType.FINAL_EXAM);
    }

    @Test
    void approvingASubjectForAStudentWithAnActiveGraduationRequestSavesTheGradeWithoutRollingBack() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000099", institution);
        Instructor instructor = persistInstructor("71000098");
        Subject subject = persistSubject("MateriaConEgresoActivo");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(0, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraConEgresoActivo");
        program.setTotalCreditsRequired(0);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        GraduationRequest activeRequest = new GraduationRequest();
        activeRequest.setProgramEnrollment(programEnrollment);
        activeRequest.setStatus(GraduationRequestStatus.REQUESTED);
        activeRequest.setRequestedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        entityManager.persist(activeRequest);
        entityManager.flush();

        GradeItem saved = gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));

        EntityManagerHolder sharedTransaction = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        assertThat(sharedTransaction.getEntityManager().getTransaction().getRollbackOnly()).isFalse();
        assertThat(saved.getId()).isNotNull();
        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void bothExamsAbovePromotionGradePromotesWithoutFinal() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000013", institution);
        Instructor instructor = persistInstructor("71000014");
        Subject subject = persistSubject("MateriaPromocionable");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(2, 0, true, 7.0);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraPromocionable");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(7.5)));
        assertThat(enrollment.getFinalGradeType()).isEqualTo(com.red_escuela.backend.evaluation.entity.FinalGradeType.PROMOTION);
    }

    @Test
    void examsBelowPromotionGradeButAbovePassingFloorLeaveEnrollmentRegular() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000015", institution);
        Instructor instructor = persistInstructor("71000016");
        Subject subject = persistSubject("MateriaCasiPromociona");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(2, 0, true, 7.0);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraCasiPromociona");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(6.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(5.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void oneFailedTpLeavesEnrollmentFailedEvenIfOverallAverageIsEnough() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000017", institution);
        Instructor instructor = persistInstructor("71000018");
        Subject subject = persistSubject("MateriaConTpBajo");
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(1, 1, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraConTpBajo");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(10.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.TP, 1, null, LocalDateTime.now(), BigDecimal.valueOf(1.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void failingFirstFinalCallLeavesEnrollmentRegularWhenAttemptsRemain() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000019", institution);
        Instructor instructor = persistInstructor("71000020");
        Subject subject = persistSubject("MateriaDosLlamados", 2);
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(0, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraDosLlamados");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(3.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void passingSecondFinalCallApprovesSubjectWithThatGrade() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000021", institution);
        Instructor instructor = persistInstructor("71000022");
        Subject subject = persistSubject("MateriaSegundoLlamadoOk", 2);
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(0, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraSegundoLlamadoOk");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(3.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 2, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(7.0)));
        assertThat(enrollment.getFinalGradeType()).isEqualTo(com.red_escuela.backend.evaluation.entity.FinalGradeType.FINAL_EXAM);
    }

    @Test
    void failingBothFinalCallsLeavesSubjectFailed() {
        Institution institution = persistInstitution();
        Student student = persistStudent("71000023", institution);
        Instructor instructor = persistInstructor("71000024");
        Subject subject = persistSubject("MateriaDosLlamadosFallidos", 2);
        Term term = persistTerm();
        EvaluationScheme scheme = persistScheme(0, 0, true);
        SubjectSection section = persistSection(subject, term, scheme);
        assignInstructor(section, instructor);
        Program program = persistProgram("CarreraDosLlamadosFallidos");
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
        SubjectSectionEnrollment enrollment = persistEnrollment(student, programEnrollment, section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(3.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 2, null, LocalDateTime.now(), BigDecimal.valueOf(2.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    private SubjectSectionEnrollment enrollmentWithScheme(String dni, EvaluationScheme scheme, Instructor instructor, Double maxAbsencePercentage) {
        Institution institution = persistInstitution();
        Student student = persistStudent(dni, institution);
        Subject subject = persistSubject("Materia " + dni);
        SubjectSection section = persistSection(subject, persistTerm(), scheme);
        section.setMaxAbsenceRate(maxAbsencePercentage == null ? null : Percentage.ofPoints(BigDecimal.valueOf(maxAbsencePercentage)));
        assignInstructor(section, instructor);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("Carrera " + dni));
        return persistEnrollment(student, programEnrollment, section);
    }

    private void persistPresences(SubjectSectionEnrollment enrollment, Instructor instructor, int count) {
        for (int day = 1; day <= count; day++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(LocalDate.of(2020, 5, day));
            attendance.setStatus(AttendanceStatus.PRESENT);
            attendance.setRecordedBy(instructor);
            entityManager.persist(attendance);
        }
    }

    private void persistAbsences(SubjectSectionEnrollment enrollment, Instructor instructor, int count) {
        for (int day = 1; day <= count; day++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(LocalDate.of(2020, 4, day));
            attendance.setStatus(AttendanceStatus.ABSENT);
            attendance.setRecordedBy(instructor);
            entityManager.persist(attendance);
        }
    }

    @Test
    void schemeCreatedWithoutAggregationMethodStillMakesAStudentWithApprovedExamsRegular() {
        EvaluationScheme scheme = evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Esquema desde la pantalla", 2, true, null, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(6.0), null, null));
        Instructor instructor = persistInstructor("71000112");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000111", scheme, instructor, null);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(6.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(scheme.getAggregationMethod()).isEqualTo(GradeAggregationMethod.SIMPLE_AVERAGE);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void loadingAScoreAboveTheMaxScoreFails() {
        Instructor instructor = persistInstructor("71000102");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000101", persistScheme(2, 1, true), instructor, null);
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.TP, 3, null, LocalDateTime.now(), BigDecimal.valueOf(15.0), null, null)))
                .isInstanceOf(InvalidGradeException.class);
        assertThat(gradeItemService.listByProgramEnrollment(enrollment.getId())).isEmpty();
    }

    @Test
    void loadingANegativeScoreOrOneAboveTheGradeScaleFails() {
        Instructor instructor = persistInstructor("71000104");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000103", persistScheme(2, 0, true), instructor, null);
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(-1.0), null, null)))
                .isInstanceOf(InvalidGradeException.class);
        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(11.0), null, null)))
                .isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void aStudentOverTheAbsenceLimitDoesNotBecomeRegularNorFailedWhileFree() {
        Instructor instructor = persistInstructor("71000106");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000105", persistScheme(2, 0, true), instructor, 0.5);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void aStudentOverTheAbsenceLimitIsNotPromotedWhileFree() {
        Instructor instructor = persistInstructor("71000108");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000107", persistScheme(2, 0, true, 7.0), instructor, 0.5);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(9.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(9.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        assertThat(enrollment.getFinalScore()).isNull();
    }

    @Test
    void justifyingAbsencesBelowTheLimitRestoresTheConditionEarnedByGrades() {
        Instructor instructor = persistInstructor("71000114");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000113", persistScheme(2, 0, true), instructor, 25.0);
        persistAbsences(enrollment, instructor, 2);
        persistPresences(enrollment, instructor, 2);
        entityManager.flush();
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), BigDecimal.valueOf(7.0), null, null));
        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.IN_PROGRESS);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(instructor.getPerson().getId().toString(), null));
        Attendance absence = attendanceRepository.findByProgramSubjectSectionEnrollmentId(enrollment.getId()).stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ABSENT).findFirst().orElseThrow();
        justifyAttendanceUseCase.justify(new JustifyAttendanceCommand(absence.getId(), "certificado médico", null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void regularStudentKeepsApprovingTheFinalRegardlessOfLaterAttendance() {
        Instructor instructor = persistInstructor("71000110");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000109", persistScheme(0, 0, true), instructor, 0.5);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(8.0), null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }
}
