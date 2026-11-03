package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.FinalExamRequiresRegularStudentException;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseGradeItemServiceTest extends AbstractIntegrationTest {

    private static final AtomicInteger DNI_SEQUENCE = new AtomicInteger(81000000);

    @Autowired
    private GradeItemService gradeItemService;

    @Autowired
    private GradeItemRepository gradeItemRepository;

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

    private Student persistStudent() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("Alumno"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private EvaluationScheme persistScheme(
            int examCount, boolean hasFinal, GradeAggregationMethod method, Double promotionMinGrade) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema de curso");
        scheme.setExamCountExpected(examCount);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(hasFinal);
        scheme.setAggregationMethod(method);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        scheme.setPromotionMinGrade(promotionMinGrade == null ? null : Grade.of(BigDecimal.valueOf(promotionMinGrade)));
        entityManager.persist(scheme);
        return scheme;
    }

    private EvaluationScheme simpleScheme(int examCount, boolean hasFinal) {
        return persistScheme(examCount, hasFinal, GradeAggregationMethod.SIMPLE_AVERAGE, null);
    }

    private record Setup(Instructor instructor, CourseSubjectSectionEnrollment enrollment,
                         CourseSubject courseSubject, Term term) {
    }

    private Setup setup(EvaluationScheme scheme, EnrollmentStatus status, Integer maxFinalExamAttempts) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        Course course = new Course();
        course.setName("Curso de notas");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName("Modulo de notas");
        if (maxFinalExamAttempts != null) {
            courseSubject.setMaxFinalExamAttempts(maxFinalExamAttempts);
        }
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision de curso");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        Instructor instructor = persistInstructor();
        CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        Student student = persistStudent();
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
        return new Setup(instructor, enrollment, courseSubject, term);
    }

    private CreateGradeItemRequest request(GradeItemType type, int number, double score) {
        return request(type, number, score, null, LocalDateTime.now());
    }

    private CreateGradeItemRequest request(GradeItemType type, int number, double score, Double weight, LocalDateTime date) {
        return new CreateGradeItemRequest(type, number, weight, date, BigDecimal.valueOf(score), null, null);
    }

    private CreateGradeItemRequest boardRequest(Long examBoardId) {
        return new CreateGradeItemRequest(
                GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(7), null, examBoardId);
    }

    private void load(Setup s, CreateGradeItemRequest request) {
        gradeItemService.loadCourseEnrollmentGrade(s.enrollment().getId(), s.instructor().getId(), request);
    }

    private EnrollmentStatus statusAfter(Setup s) {
        entityManager.flush();
        entityManager.refresh(s.enrollment());
        return s.enrollment().getStatus();
    }

    private ExamBoard persistExamBoard(Setup s, ExamBoardStatus status) {
        ExamBoard board = new ExamBoard();
        board.setCourseSubject(s.courseSubject());
        board.setTerm(s.term());
        board.setExamDate(LocalDateTime.now().plusDays(3));
        board.setStatus(status);
        entityManager.persist(board);
        entityManager.flush();
        return board;
    }

    @Test
    void loadingACourseGradeWithoutAnAssignedSchemeFails() {
        Setup s = setup(null, EnrollmentStatus.IN_PROGRESS, null);

        assertThatThrownBy(() -> load(s, request(GradeItemType.EXAM, 1, 7)))
                .isInstanceOf(NoEvaluationSchemeException.class);
    }

    @Test
    void anInstructorOfAnotherCourseSectionCannotLoadACourseGrade() {
        Setup s = setup(simpleScheme(1, false), EnrollmentStatus.IN_PROGRESS, null);
        Instructor stranger = persistInstructor();
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemService.loadCourseEnrollmentGrade(
                s.enrollment().getId(), stranger.getId(), request(GradeItemType.EXAM, 1, 8)))
                .isInstanceOf(NotOwnSectionException.class);
        assertThat(gradeItemRepository.findByCourseSubjectSectionEnrollmentId(s.enrollment().getId())).isEmpty();
    }

    @Test
    void courseExamsAveragingBelowTheRegularMinimumFailTheEnrollment() {
        Setup s = setup(simpleScheme(2, false), EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 2));
        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.IN_PROGRESS);
        load(s, request(GradeItemType.EXAM, 2, 3));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void courseExamsAveragingAboveTheApprovalMinimumWithoutFinalApproveByPromotion() {
        Setup s = setup(simpleScheme(2, false), EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 7));
        load(s, request(GradeItemType.EXAM, 2, 9));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(s.enrollment().getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
    }

    @Test
    void courseExamsAboveThePromotionGradePromoteEvenWhenTheSchemeHasAFinal() {
        Setup s = setup(persistScheme(2, true, GradeAggregationMethod.SIMPLE_AVERAGE, 8.0),
                EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 8));
        load(s, request(GradeItemType.EXAM, 2, 9));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(s.enrollment().getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
    }

    @Test
    void courseExamsAboveTheRegularMinimumWithAFinalLeaveTheEnrollmentRegular() {
        Setup s = setup(simpleScheme(2, true), EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 5));
        load(s, request(GradeItemType.EXAM, 2, 5));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void loadingTheSameCourseExamTwiceCorrectsTheGradeInsteadOfDuplicatingIt() {
        Setup s = setup(simpleScheme(2, true), EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 4));
        load(s, request(GradeItemType.EXAM, 1, 9));

        assertThat(gradeItemService.listByCourseEnrollment(s.enrollment().getId()))
                .singleElement()
                .satisfies(item -> assertThat(item.getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(9.0))));
    }

    @Test
    void aCourseFinalCannotBeLoadedForAStudentWhoIsNotRegular() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.IN_PROGRESS, null);

        assertThatThrownBy(() -> load(s, request(GradeItemType.FINAL, 1, 8)))
                .isInstanceOf(FinalExamRequiresRegularStudentException.class);
        assertThat(gradeItemRepository.findByCourseSubjectSectionEnrollmentId(s.enrollment().getId())).isEmpty();
    }

    @Test
    void passingTheCourseFinalAsARegularStudentApprovesWithAFinalExam() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, null);

        load(s, request(GradeItemType.FINAL, 1, 7));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(s.enrollment().getFinalScore()).isEqualTo(Grade.of(BigDecimal.valueOf(7.0)));
        assertThat(s.enrollment().getFinalGradeType()).isEqualTo(FinalGradeType.FINAL_EXAM);
    }

    @Test
    void failingTheOnlyCourseFinalAttemptFailsTheEnrollment() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, 1);

        load(s, request(GradeItemType.FINAL, 1, 3));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void failingACourseFinalWithAttemptsLeftKeepsTheStudentRegular() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, 2);

        load(s, request(GradeItemType.FINAL, 1, 3));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void correctingAnExistingCourseFinalDoesNotRequireEligibilityAgain() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, 2);
        load(s, request(GradeItemType.FINAL, 1, 3));

        assertThatCode(() -> load(s, request(GradeItemType.FINAL, 1, 8))).doesNotThrowAnyException();
        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void loadingAGradeForAnUnknownExamBoardFails() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, null);

        assertThatThrownBy(() -> load(s, boardRequest(999_999_999L)))
                .isInstanceOf(ExamBoardNotFoundException.class);
    }

    @Test
    void loadingAGradeForAClosedExamBoardFails() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, null);
        ExamBoard closed = persistExamBoard(s, ExamBoardStatus.CLOSED);

        assertThatThrownBy(() -> load(s, boardRequest(closed.getId())))
                .isInstanceOf(ExamBoardClosedException.class);
    }

    @Test
    void anInstructorOutsideTheExamBoardTribunalCannotLoadItsGrades() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, null);
        ExamBoard board = persistExamBoard(s, ExamBoardStatus.SCHEDULED);

        assertThatThrownBy(() -> load(s, boardRequest(board.getId())))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void aTribunalMemberLoadsTheFinalGradeOfTheExamBoardEvenWithoutTeachingTheSection() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.REGULAR, null);
        Instructor tribunalMember = persistInstructor();
        ExamBoard board = persistExamBoard(s, ExamBoardStatus.SCHEDULED);
        ExamBoardInstructor member = new ExamBoardInstructor();
        member.setExamBoard(board);
        member.setInstructor(tribunalMember);
        entityManager.persist(member);
        entityManager.flush();

        var item = gradeItemService.loadCourseEnrollmentGrade(
                s.enrollment().getId(), tribunalMember.getId(), boardRequest(board.getId()));

        assertThat(item.getExamBoard().getId()).isEqualTo(board.getId());
        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void weightedAverageGivesMoreInfluenceToTheHeavierExam() {
        Setup s = setup(persistScheme(2, true, GradeAggregationMethod.WEIGHTED_AVERAGE, null),
                EnrollmentStatus.IN_PROGRESS, null);
        LocalDateTime now = LocalDateTime.now();

        load(s, request(GradeItemType.EXAM, 1, 2, 0.9, now));
        load(s, request(GradeItemType.EXAM, 2, 8, 0.1, now));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.FAILED);
    }

    @Test
    void weightedAverageWithoutWeightsFallsBackToTheSimpleAverage() {
        Setup s = setup(persistScheme(2, true, GradeAggregationMethod.WEIGHTED_AVERAGE, null),
                EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 3));
        load(s, request(GradeItemType.EXAM, 2, 5));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void maxScoreAggregationUsesTheBestExamGrade() {
        Setup s = setup(persistScheme(2, true, GradeAggregationMethod.MAX_SCORE, null),
                EnrollmentStatus.IN_PROGRESS, null);

        load(s, request(GradeItemType.EXAM, 1, 1));
        load(s, request(GradeItemType.EXAM, 2, 6));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void lastAttemptAggregationUsesTheMostRecentExamByDateNotBySequence() {
        Setup s = setup(persistScheme(2, true, GradeAggregationMethod.LAST_ATTEMPT, null),
                EnrollmentStatus.IN_PROGRESS, null);
        LocalDateTime now = LocalDateTime.now();

        load(s, request(GradeItemType.EXAM, 1, 6, null, now));
        load(s, request(GradeItemType.EXAM, 2, 1, null, now.minusDays(5)));

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void recalculatingTheConditionOfAnUnknownEnrollmentDoesNothing() {
        assertThatCode(() -> gradeItemService.recalculateCourseEnrollmentCondition(999_999_999L))
                .doesNotThrowAnyException();
        assertThatCode(() -> gradeItemService.recalculateProgramEnrollmentCondition(999_999_999L))
                .doesNotThrowAnyException();
    }

    @Test
    void recalculatingTheConditionOfACourseEnrollmentWithoutSchemeKeepsItsStatus() {
        Setup s = setup(null, EnrollmentStatus.IN_PROGRESS, null);

        gradeItemService.recalculateCourseEnrollmentCondition(s.enrollment().getId());

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void recalculatingTheConditionSettlesAnEnrollmentWhoseExamsAreAlreadyComplete() {
        Setup s = setup(simpleScheme(1, true), EnrollmentStatus.IN_PROGRESS, null);
        load(s, request(GradeItemType.EXAM, 1, 2));
        s.enrollment().setStatus(EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();

        gradeItemService.recalculateCourseEnrollmentCondition(s.enrollment().getId());

        assertThat(statusAfter(s)).isEqualTo(EnrollmentStatus.FAILED);
    }
}
