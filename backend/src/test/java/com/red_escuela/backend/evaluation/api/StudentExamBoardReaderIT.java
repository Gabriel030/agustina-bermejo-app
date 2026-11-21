package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentExamBoardReaderIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime NOW = LocalDateTime.of(2170, 6, 10, 12, 0);
    private static final LocalDate FROM = LocalDate.of(2170, 6, 15);
    private static final LocalDate TO = LocalDate.of(2170, 6, 30);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ExamBoardRegistrationRepository registrationRepository;
    @Autowired
    private ExamBoardRepository examBoardRepository;

    private StudentExamBoardReader reader;
    private CycleTestFixtures fixtures;
    private Term term;
    private Institution institution;
    private Student student;
    private Student otherStudent;
    private ProgramEnrollment programEnrollment;
    private ProgramEnrollment otherProgramEnrollment;
    private Course course;
    private CourseEnrollment courseEnrollment;

    @BeforeEach
    void seedStudents() {
        reader = new StudentExamBoardReader(registrationRepository, examBoardRepository,
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2170);
        institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        student = student();
        otherStudent = student();
        var program = fixtures.program();
        programEnrollment = programEnrollment(student, program);
        otherProgramEnrollment = programEnrollment(otherStudent, program);
        course = course();
        courseEnrollment = courseEnrollment(student);
    }

    @Test
    void registeredProgramBoardIsReturnedWithItsRegistrationStatusAndOpenWindow() {
        Subject subject = fixtures.subject();
        SubjectSectionEnrollment enrollment = programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        ExamBoard board = programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        register(board, enrollment, null, ExamBoardRegistrationStatus.REGISTERED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).containsExactly(
                new StudentExamBoardView(board.getId(), subject.getName(), FROM.atTime(9, 0), "Aula 1",
                        "SCHEDULED", "REGISTERED", true));
    }

    @Test
    void registeredCourseBoardIsReturnedAndCanceledBoardKeepsItsStatus() {
        CourseSubjectSection section = courseSectionEnrollment(student, courseEnrollment, EnrollmentStatus.IN_PROGRESS);
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentOf(section);
        ExamBoard board = courseBoard(section.getCourseSubject(), FROM.atTime(10, 0), ExamBoardStatus.CANCELED, null, null);
        register(board, null, enrollment, ExamBoardRegistrationStatus.REGISTERED);
        entityManager.flush();
        entityManager.clear();

        List<StudentExamBoardView> views = reader.listForStudent(student.getId(), FROM, TO);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).boardStatus()).isEqualTo("CANCELED");
        assertThat(views.get(0).registrationStatus()).isEqualTo("REGISTERED");
        assertThat(views.get(0).registrationOpen()).isFalse();
        assertThat(views.get(0).subjectName()).isEqualTo(section.getCourseSubject().getName());
    }

    @Test
    void registeredStatusWinsOverWithdrawnWhenBothRegistrationsTargetTheSameBoard() {
        Subject subject = fixtures.subject();
        SubjectSectionEnrollment withdrawnEnrollment = programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        SubjectSectionEnrollment registeredEnrollment = programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        ExamBoard board = programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        register(board, withdrawnEnrollment, null, ExamBoardRegistrationStatus.WITHDRAWN);
        register(board, registeredEnrollment, null, ExamBoardRegistrationStatus.REGISTERED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).containsExactly(
                new StudentExamBoardView(board.getId(), subject.getName(), FROM.atTime(9, 0), "Aula 1",
                        "SCHEDULED", "REGISTERED", true));
    }

    @Test
    void openBoardOfAttendedProgramSubjectIsOfferedWhenNotRegistered() {
        Subject subject = fixtures.subject();
        programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.REGULAR);
        ExamBoard board = programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).containsExactly(
                new StudentExamBoardView(board.getId(), subject.getName(), FROM.atTime(9, 0), "Aula 1",
                        "SCHEDULED", null, true));
    }

    @Test
    void openBoardOfAttendedCourseSubjectIsOfferedWhenNotRegistered() {
        CourseSubjectSection section = courseSectionEnrollment(student, courseEnrollment, EnrollmentStatus.ENROLLED);
        ExamBoard board = courseBoard(section.getCourseSubject(), FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO))
                .extracting(StudentExamBoardView::boardId, StudentExamBoardView::registrationStatus, StudentExamBoardView::registrationOpen)
                .containsExactly(Tuple.tuple(board.getId(), null, true));
    }

    @Test
    void closedWindowFutureWindowCanceledAndClosedBoardsAreNotOffered() {
        Subject subject = fixtures.subject();
        programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(5), NOW.minusSeconds(1));
        programBoard(subject, FROM.atTime(10, 0), ExamBoardStatus.SCHEDULED, NOW.plusSeconds(1), NOW.plusDays(5));
        programBoard(subject, FROM.atTime(11, 0), ExamBoardStatus.CANCELED, NOW.minusDays(1), NOW.plusDays(1));
        programBoard(subject, FROM.atTime(12, 0), ExamBoardStatus.CLOSED, NOW.minusDays(1), NOW.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).isEmpty();
    }

    @Test
    void windowEdgesAtExactlyNowAreOpen() {
        Subject subject = fixtures.subject();
        programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW, NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).hasSize(1);
    }

    @Test
    void boardsOfSubjectsNotActivelyAttendedAreNotOffered() {
        Subject withdrawn = fixtures.subject();
        Subject approved = fixtures.subject();
        Subject waitlisted = fixtures.subject();
        Subject failed = fixtures.subject();
        programEnrollmentIn(student, programEnrollment, withdrawn, EnrollmentStatus.WITHDRAWN);
        programEnrollmentIn(student, programEnrollment, approved, EnrollmentStatus.APPROVED);
        programEnrollmentIn(student, programEnrollment, waitlisted, EnrollmentStatus.WAITLISTED);
        programEnrollmentIn(student, programEnrollment, failed, EnrollmentStatus.FAILED);
        for (Subject subject : List.of(withdrawn, approved, waitlisted, failed)) {
            programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).isEmpty();
    }

    @Test
    void dateRangeIsInclusiveOfTheWholeLastDay() {
        Subject subject = fixtures.subject();
        programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        ExamBoard firstMoment = programBoard(subject, FROM.atStartOfDay(), ExamBoardStatus.SCHEDULED, null, null);
        ExamBoard lastMoment = programBoard(subject, TO.atTime(23, 59), ExamBoardStatus.SCHEDULED, null, null);
        programBoard(subject, FROM.atStartOfDay().minusMinutes(1), ExamBoardStatus.SCHEDULED, null, null);
        programBoard(subject, TO.plusDays(1).atStartOfDay(), ExamBoardStatus.SCHEDULED, null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).extracting(StudentExamBoardView::boardId)
                .containsExactly(firstMoment.getId(), lastMoment.getId());
    }

    @Test
    void withdrawnRegistrationOfAnOpenBoardIsOfferedAgainOnce() {
        Subject subject = fixtures.subject();
        SubjectSectionEnrollment enrollment = programEnrollmentIn(student, programEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        ExamBoard board = programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        register(board, enrollment, null, ExamBoardRegistrationStatus.WITHDRAWN);
        entityManager.flush();
        entityManager.clear();

        List<StudentExamBoardView> views = reader.listForStudent(student.getId(), FROM, TO);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).registrationStatus()).isEqualTo("WITHDRAWN");
        assertThat(views.get(0).registrationOpen()).isTrue();
    }

    @Test
    void aStudentNeverSeesTheBoardsOrRegistrationsOfAnotherStudent() {
        Subject subject = fixtures.subject();
        SubjectSectionEnrollment otherEnrollment = programEnrollmentIn(otherStudent, otherProgramEnrollment, subject, EnrollmentStatus.IN_PROGRESS);
        ExamBoard board = programBoard(subject, FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED, NOW.minusDays(1), NOW.plusDays(1));
        register(board, otherEnrollment, null, ExamBoardRegistrationStatus.REGISTERED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).isEmpty();
        assertThat(reader.listForStudent(otherStudent.getId(), FROM, TO)).hasSize(1);
    }

    @Test
    void courseBoardsAndRegistrationsOfAnotherStudentAreNotOffered() {
        CourseEnrollment otherCourseEnrollment = courseEnrollment(otherStudent);
        CourseSubjectSection otherSection = courseSectionEnrollment(otherStudent, otherCourseEnrollment, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment otherEnrollment = courseSubjectSectionEnrollmentOf(otherSection);
        ExamBoard board = courseBoard(otherSection.getCourseSubject(), FROM.atTime(9, 0), ExamBoardStatus.SCHEDULED,
                NOW.minusDays(1), NOW.plusDays(1));
        register(board, null, otherEnrollment, ExamBoardRegistrationStatus.REGISTERED);
        entityManager.flush();
        entityManager.clear();

        assertThat(reader.listForStudent(student.getId(), FROM, TO)).isEmpty();
        assertThat(reader.listForStudent(otherStudent.getId(), FROM, TO)).hasSize(1);
    }

    private CourseSubjectSectionEnrollment courseSubjectSectionEnrollmentOf(CourseSubjectSection section) {
        return entityManager.createQuery(
                "select e from CourseSubjectSectionEnrollment e where e.courseSubjectSection = :section",
                CourseSubjectSectionEnrollment.class).setParameter("section", section).getSingleResult();
    }

    private Student student() {
        Student created = new Student();
        created.setPerson(fixtures.person());
        created.setInstitution(institution);
        created.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(created);
        return created;
    }

    private ProgramEnrollment programEnrollment(Student owner, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(owner);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private SubjectSectionEnrollment programEnrollmentIn(Student owner, ProgramEnrollment parent, Subject subject, EnrollmentStatus status) {
        var cycle = fixtures.programCycle(parent.getProgram(), term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(owner);
        enrollment.setProgramEnrollment(parent);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Course course() {
        Course created = new Course();
        created.setName("Curso 2170");
        created.setTerm(term);
        created.setCapacity(30);
        created.setIsActive(StatusType.ACTIVE);
        created.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(created);
        return created;
    }

    private CourseEnrollment courseEnrollment(Student owner) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(owner);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private CourseSubjectSection courseSectionEnrollment(Student owner, CourseEnrollment parent, EnrollmentStatus status) {
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Módulo " + System.nanoTime());
        module.setStatus(StatusType.ACTIVE);
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(owner);
        enrollment.setCourseEnrollment(parent);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return section;
    }

    private ExamBoard programBoard(Subject subject, LocalDateTime examDate, ExamBoardStatus status,
                                   LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        ExamBoard board = board(examDate, status, registrationStart, registrationEnd);
        board.setSubject(subject);
        entityManager.persist(board);
        return board;
    }

    private ExamBoard courseBoard(CourseSubject courseSubject, LocalDateTime examDate, ExamBoardStatus status,
                                  LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        ExamBoard board = board(examDate, status, registrationStart, registrationEnd);
        board.setCourseSubject(courseSubject);
        entityManager.persist(board);
        return board;
    }

    private ExamBoard board(LocalDateTime examDate, ExamBoardStatus status,
                            LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        ExamBoard board = new ExamBoard();
        board.setTerm(term);
        board.setExamDate(examDate);
        board.setClassroom("Aula 1");
        board.setStatus(status);
        board.setRegistrationStart(registrationStart);
        board.setRegistrationEnd(registrationEnd);
        return board;
    }

    private void register(ExamBoard board, SubjectSectionEnrollment program, CourseSubjectSectionEnrollment courseEnrollment,
                          ExamBoardRegistrationStatus status) {
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(program);
        registration.setCourseSubjectSectionEnrollment(courseEnrollment);
        registration.setStatus(status);
        registration.setRegisteredAt(NOW.minusDays(2));
        entityManager.persist(registration);
    }
}
