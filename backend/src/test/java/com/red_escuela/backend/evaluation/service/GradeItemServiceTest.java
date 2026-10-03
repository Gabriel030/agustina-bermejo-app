package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.entity.Address;
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
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
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
import com.red_escuela.backend.evaluation.exception.InvalidGradeScoreException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GradeItemServiceTest extends AbstractIntegrationTest {

    @Autowired
    private GradeItemService gradeItemService;

    @Autowired
    private EntityManager entityManager;

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
        scheme.setMinRegularGrade(4.0);
        scheme.setMinApprovalGrade(6.0);
        scheme.setPromotionMinGrade(promotionMinGrade);
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
                GradeItemType.EXAM, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null);

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
                GradeItemType.EXAM, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null);

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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 2.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 3.0, 10.0, null, null));

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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 7.0, 10.0, null, null));

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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 2.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 9.0, 10.0, "corregida", null));

        var items = gradeItemService.listByProgramEnrollment(enrollment.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getScore()).isEqualTo(9.0);
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
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(8.0);
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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 7.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(7.5);
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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 6.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 5.0, 10.0, null, null));

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
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 10.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.TP, 1, null, LocalDateTime.now(), 1.0, 10.0, null, null));

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
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), 3.0, 10.0, null, null));

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
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), 3.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 2, null, LocalDateTime.now(), 7.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment.getFinalScore()).isEqualTo(7.0);
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
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), 3.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 2, null, LocalDateTime.now(), 2.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    private SubjectSectionEnrollment enrollmentWithScheme(String dni, EvaluationScheme scheme, Instructor instructor, Double maxAbsencePercentage) {
        Institution institution = persistInstitution();
        Student student = persistStudent(dni, institution);
        Subject subject = persistSubject("Materia " + dni);
        SubjectSection section = persistSection(subject, persistTerm(), scheme);
        section.setMaxAbsencePercentage(maxAbsencePercentage);
        assignInstructor(section, instructor);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, persistProgram("Carrera " + dni));
        return persistEnrollment(student, programEnrollment, section);
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
    void loadingAScoreAboveTheMaxScoreFails() {
        Instructor instructor = persistInstructor("71000102");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000101", persistScheme(2, 1, true), instructor, null);
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.TP, 3, null, LocalDateTime.now(), 15.0, 10.0, null, null)))
                .isInstanceOf(InvalidGradeScoreException.class);
        assertThat(gradeItemService.listByProgramEnrollment(enrollment.getId())).isEmpty();
    }

    @Test
    void loadingANegativeScoreOrOneAboveTheGradeScaleFails() {
        Instructor instructor = persistInstructor("71000104");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000103", persistScheme(2, 0, true), instructor, null);
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), -1.0, 10.0, null, null)))
                .isInstanceOf(InvalidGradeScoreException.class);
        assertThatThrownBy(() -> gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 11.0, null, null, null)))
                .isInstanceOf(InvalidGradeScoreException.class);
    }

    @Test
    void studentOverTheAbsenceLimitDoesNotBecomeRegularByGrades() {
        Instructor instructor = persistInstructor("71000106");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000105", persistScheme(2, 0, true), instructor, 0.5);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 7.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void studentOverTheAbsenceLimitIsNotPromoted() {
        Instructor instructor = persistInstructor("71000108");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000107", persistScheme(2, 0, true, 7.0), instructor, 0.5);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), 9.0, 10.0, null, null));
        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 2, null, LocalDateTime.now(), 9.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(enrollment.getFinalScore()).isNull();
    }

    @Test
    void regularStudentKeepsApprovingTheFinalRegardlessOfLaterAttendance() {
        Instructor instructor = persistInstructor("71000110");
        SubjectSectionEnrollment enrollment = enrollmentWithScheme("71000109", persistScheme(0, 0, true), instructor, 0.5);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        persistAbsences(enrollment, instructor, 4);
        entityManager.flush();

        gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), 8.0, 10.0, null, null));

        entityManager.flush();
        entityManager.refresh(enrollment);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }
}
