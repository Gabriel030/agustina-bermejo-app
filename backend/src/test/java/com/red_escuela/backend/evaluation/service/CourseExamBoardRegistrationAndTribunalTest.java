package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotWithdrawableException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.evaluation.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import com.red_escuela.backend.evaluation.exception.StudentNotRegularException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseExamBoardRegistrationAndTribunalTest extends AbstractIntegrationTest {

    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(82000000);

    @Autowired
    private ExamBoardRegistrationService registrationService;

    @Autowired
    private ExamBoardService examBoardService;

    @Autowired
    private EntityManager entityManager;

    private Person persistPerson(String name) {
        String dni = String.valueOf(DNI_SEQUENCE.incrementAndGet());
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Instructor persistInstructor() {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPerson("Docente"));
        entityManager.persist(instructor);
        return instructor;
    }

    private record World(Term term, Course course, CourseSubject courseSubject, CourseSubjectSection section) {
    }

    private World world(Integer maxAttempts) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        Course course = new Course();
        course.setName("Curso de mesas");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName("Modulo de mesas");
        if (maxAttempts != null) {
            courseSubject.setMaxFinalExamAttempts(maxAttempts);
        }
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision de mesas");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return new World(term, course, courseSubject, section);
    }

    private CourseSubjectSectionEnrollment persistEnrollment(World w, EnrollmentStatus status) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("Alumno"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(w.course());
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(w.section());
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
        return enrollment;
    }

    private ExamBoard persistBoard(World w, ExamBoardStatus status, LocalDateTime registrationEnd) {
        ExamBoard board = new ExamBoard();
        board.setCourseSubject(w.courseSubject());
        board.setTerm(w.term());
        board.setExamDate(LocalDateTime.now().plusDays(10));
        board.setRegistrationEnd(registrationEnd);
        board.setStatus(status);
        entityManager.persist(board);
        entityManager.flush();
        return board;
    }

    private ExamBoard scheduledBoard(World w) {
        return persistBoard(w, ExamBoardStatus.SCHEDULED, null);
    }

    private void addToTribunal(ExamBoard board, Instructor instructor) {
        examBoardService.assignTribunal(board.getId(), instructor.getId());
        entityManager.flush();
    }

    @Test
    void aRegularCourseStudentRegistersForAScheduledExamBoard() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(w);

        ExamBoardRegistration registration = registrationService.registerCourseEnrollment(board.getId(), enrollment.getId());

        assertThat(registration.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
        assertThat(registrationService.get(registration.getId()).getCourseSubjectSectionEnrollment().getId())
                .isEqualTo(enrollment.getId());
    }

    @Test
    void aCourseStudentWhoIsNotRegularCannotRegisterForAnExamBoard() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.IN_PROGRESS);
        ExamBoard board = scheduledBoard(w);

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(board.getId(), enrollment.getId()))
                .isInstanceOf(StudentNotRegularException.class);
    }

    @Test
    void registeringForAClosedCourseExamBoardFails() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard closed = persistBoard(w, ExamBoardStatus.CLOSED, null);

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(closed.getId(), enrollment.getId()))
                .isInstanceOf(ExamBoardClosedException.class);
    }

    @Test
    void registeringAfterTheRegistrationWindowEndedFails() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard board = persistBoard(w, ExamBoardStatus.SCHEDULED, LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(board.getId(), enrollment.getId()))
                .isInstanceOf(ExamBoardRegistrationWindowClosedException.class);
    }

    @Test
    void registeringForAnUnknownExamBoardFails() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(999_999_999L, enrollment.getId()))
                .isInstanceOf(ExamBoardNotFoundException.class);
    }

    @Test
    void registeringForAnExamBoardOfAnotherCourseSubjectFails() {
        World w = world(null);
        World other = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard foreignBoard = scheduledBoard(other);

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(foreignBoard.getId(), enrollment.getId()))
                .isInstanceOf(ExamBoardSubjectMismatchException.class);
    }

    @Test
    void registeringTwiceForTheSameCourseExamBoardFails() {
        World w = world(2);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(w);
        registrationService.registerCourseEnrollment(board.getId(), enrollment.getId());

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(board.getId(), enrollment.getId()))
                .isInstanceOf(AlreadyRegisteredInExamBoardException.class);
    }

    @Test
    void aCourseStudentCannotHoldRegistrationsInTwoExamBoardsOfTheSameSubject() {
        World w = world(3);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard first = scheduledBoard(w);
        ExamBoard second = scheduledBoard(w);
        registrationService.registerCourseEnrollment(first.getId(), enrollment.getId());

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(second.getId(), enrollment.getId()))
                .isInstanceOf(AlreadyRegisteredInAnotherExamBoardException.class);
    }

    @Test
    void withdrawingFromACourseExamBoardEnablesRegisteringInAnotherAndReRegisteringReactivatesTheRow() {
        World w = world(3);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard first = scheduledBoard(w);
        ExamBoard second = scheduledBoard(w);
        ExamBoardRegistration original = registrationService.registerCourseEnrollment(first.getId(), enrollment.getId());
        registrationService.withdraw(original.getId());

        registrationService.withdraw(registrationService.registerCourseEnrollment(second.getId(), enrollment.getId()).getId());
        ExamBoardRegistration reactivated = registrationService.registerCourseEnrollment(first.getId(), enrollment.getId());

        assertThat(reactivated.getId()).isEqualTo(original.getId());
        assertThat(reactivated.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void reachingTheCourseFinalAttemptLimitBlocksANewRegistration() {
        World w = world(1);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard first = scheduledBoard(w);
        ExamBoard second = scheduledBoard(w);
        ExamBoardRegistration used = registrationService.registerCourseEnrollment(first.getId(), enrollment.getId());
        used.setStatus(ExamBoardRegistrationStatus.GRADED);
        entityManager.flush();

        assertThatThrownBy(() -> registrationService.registerCourseEnrollment(second.getId(), enrollment.getId()))
                .isInstanceOf(MaxExamAttemptsExceededException.class);
    }

    @Test
    void aWithdrawnCourseRegistrationCannotBeWithdrawnAgainAndAnUnknownOneIsNotFound() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(w);
        ExamBoardRegistration registration = registrationService.registerCourseEnrollment(board.getId(), enrollment.getId());
        registrationService.withdraw(registration.getId());

        assertThatThrownBy(() -> registrationService.withdraw(registration.getId()))
                .isInstanceOf(ExamBoardRegistrationNotWithdrawableException.class);
        assertThatThrownBy(() -> registrationService.withdraw(999_999_999L))
                .isInstanceOf(ExamBoardRegistrationNotFoundException.class);
        assertThatThrownBy(() -> registrationService.get(999_999_999L))
                .isInstanceOf(ExamBoardRegistrationNotFoundException.class);
    }

    @Test
    void assigningTheSameInstructorToTheTribunalTwiceKeepsASingleMembership() {
        World w = world(null);
        ExamBoard board = scheduledBoard(w);
        Instructor instructor = persistInstructor();

        addToTribunal(board, instructor);
        addToTribunal(board, instructor);

        entityManager.clear();
        Long memberships = entityManager.createQuery(
                        "select count(m) from ExamBoardInstructor m where m.examBoard.id = :board", Long.class)
                .setParameter("board", board.getId()).getSingleResult();
        assertThat(memberships).isEqualTo(1L);
    }

    @Test
    void anInstructorOnlyListsTheExamBoardsWhereHeIsATribunalMember() {
        World w = world(null);
        ExamBoard mine = scheduledBoard(w);
        ExamBoard notMine = scheduledBoard(w);
        ExamBoard closedMine = persistBoard(w, ExamBoardStatus.CLOSED, null);
        Instructor instructor = persistInstructor();
        addToTribunal(mine, instructor);
        addToTribunal(closedMine, instructor);

        List<ExamBoard> all = examBoardService.listForTribunalMember(instructor.getId(), null);
        List<ExamBoard> scheduledOnly = examBoardService.listForTribunalMember(instructor.getId(), ExamBoardStatus.SCHEDULED);

        assertThat(all).extracting(ExamBoard::getId).containsExactlyInAnyOrder(mine.getId(), closedMine.getId())
                .doesNotContain(notMine.getId());
        assertThat(scheduledOnly).extracting(ExamBoard::getId).containsExactly(mine.getId());
    }

    @Test
    void anInstructorWithoutExamBoardsListsNothing() {
        Instructor instructor = persistInstructor();
        entityManager.flush();

        assertThat(examBoardService.listForTribunalMember(instructor.getId(), null)).isEmpty();
    }

    @Test
    void requiringTribunalMembershipRejectsAnInstructorOutsideTheTribunal() {
        World w = world(null);
        ExamBoard board = scheduledBoard(w);
        Instructor member = persistInstructor();
        Instructor outsider = persistInstructor();
        addToTribunal(board, member);

        assertThatCode(() -> examBoardService.requireTribunalMember(board.getId(), member.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> examBoardService.requireTribunalMember(board.getId(), outsider.getId()))
                .isInstanceOf(NotExamBoardTribunalMemberException.class);
        assertThatThrownBy(() -> examBoardService.requireTribunalMember(999_999_999L, member.getId()))
                .isInstanceOf(ExamBoardNotFoundException.class);
    }

    @Test
    void listingFiltersExamBoardsByStatusAndGettingAnUnknownOneFails() {
        World w = world(null);
        ExamBoard scheduled = scheduledBoard(w);
        ExamBoard closed = persistBoard(w, ExamBoardStatus.CLOSED, null);

        assertThat(examBoardService.list(ExamBoardStatus.CLOSED)).extracting(ExamBoard::getId)
                .contains(closed.getId()).doesNotContain(scheduled.getId());
        assertThat(examBoardService.list(null)).extracting(ExamBoard::getId)
                .contains(closed.getId(), scheduled.getId());
        assertThat(examBoardService.get(scheduled.getId()).getId()).isEqualTo(scheduled.getId());
        assertThatThrownBy(() -> examBoardService.get(999_999_999L)).isInstanceOf(ExamBoardNotFoundException.class);
    }

    @Test
    void anExamBoardMustReferenceExactlyOneSubjectKind() {
        LocalDateTime date = LocalDateTime.now().plusDays(10);

        assertThatThrownBy(() -> examBoardService.create(new CreateExamBoardRequest(1L, 2L, 1L, date, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> examBoardService.create(new CreateExamBoardRequest(null, null, 1L, date, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrantsOfACourseExamBoardAreListedWithTheCourseLabel() {
        World w = world(null);
        CourseSubjectSectionEnrollment enrollment = persistEnrollment(w, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(w);
        registrationService.registerCourseEnrollment(board.getId(), enrollment.getId());

        var summaries = examBoardService.listRegistrantsWithDetail(board.getId());

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.status()).isEqualTo("REGISTERED");
            assertThat(summary.finalScore()).isNull();
        });
        assertThatThrownBy(() -> examBoardService.listRegistrants(999_999_999L))
                .isInstanceOf(ExamBoardNotFoundException.class);
    }
}
