package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.exception.SectionClosureClosedException;
import com.red_escuela.backend.evaluation.exception.GradeCorrectionPeriodEndedException;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.RecordExamRecoveryRequest;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvaluationFindingsReproductionIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private SectionGradeItemPublisher sectionGradeItemPublisher;

    @Autowired
    private PublishedGradeCorrector publishedGradeCorrector;

    @Autowired
    private ExamRecoveryRecorder examRecoveryRecorder;

    @Autowired
    private ExamBoardManager examBoardManager;

    @Autowired
    private ExamBoardRegistrationManager examBoardRegistrationManager;

    private AcademicReproductionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private record Setup(Instructor instructor, SubjectSection section, SubjectSectionEnrollment enrollment) {
    }

    private Setup setup(String dni, int examCount, int tpCount, boolean hasFinal, EnrollmentStatus status) {
        Instructor instructor = fixtures.instructor(dni);
        Subject subject = fixtures.subject("Materia" + dni);
        SubjectSection section = fixtures.section(subject, fixtures.openTerm(), fixtures.scheme(examCount, tpCount, hasFinal));
        fixtures.assignInstructor(section, instructor);
        var student = fixtures.student(dni + "1");
        var programEnrollment = fixtures.programEnrollment(student, fixtures.program("Carrera" + dni),
                com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = fixtures.sectionEnrollment(student, programEnrollment, section, status);
        entityManager.flush();
        return new Setup(instructor, section, enrollment);
    }

    private GradeItem loadItem(Setup setup, GradeItemType type, int sequence, double score) {
        return gradeItemRecorder.loadProgramEnrollmentGrade(setup.enrollment().getId(), setup.instructor().getId(),
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.now(), BigDecimal.valueOf(score), null, null, null));
    }

    private ExamBoard scheduledBoard(Setup setup, LocalDateTime examDate, LocalDateTime registrationEnd) {
        ExamBoard board = new ExamBoard();
        board.setSubject(setup.section().getSubject());
        board.setTerm(setup.section().getTerm());
        board.setExamDate(examDate);
        board.setRegistrationEnd(registrationEnd);
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        ExamBoardInstructor tribunal = new ExamBoardInstructor();
        tribunal.setExamBoard(board);
        tribunal.setInstructor(setup.instructor());
        entityManager.persist(tribunal);
        entityManager.flush();
        return board;
    }

    private ExamBoardRegistration register(ExamBoard board, SubjectSectionEnrollment enrollment) {
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        entityManager.persist(registration);
        entityManager.flush();
        return registration;
    }

    private GradeItem loadFinal(Setup setup, ExamBoard board, double score, String reason) {
        return gradeItemRecorder.loadProgramEnrollmentGrade(setup.enrollment().getId(), setup.instructor().getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(score),
                        null, board.getId(), reason));
    }

    private void publishExam(Setup setup, int sequence) {
        sectionGradeItemPublisher.publishProgramSection(setup.section().getId(), setup.instructor().getId(),
                setup.instructor().getPerson().getId(), new PublishGradeItemRequest(GradeItemType.EXAM, sequence));
        entityManager.flush();
    }

    @Test
    void h3LoadingAGradeIsRejectedWhileTheClosureIsClosed() {
        Setup setup = setup("74000001", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, setup.instructor().getPerson().getId());

        assertThatThrownBy(() -> loadItem(setup, GradeItemType.EXAM, 1, 7.0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h3EditingAGradeIsRejectedWhileTheClosureIsClosed() {
        Setup setup = setup("74000003", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        loadItem(setup, GradeItemType.EXAM, 1, 7.0);
        entityManager.flush();
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, setup.instructor().getPerson().getId());

        assertThatThrownBy(() -> loadItem(setup, GradeItemType.EXAM, 1, 2.0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h3h16RecoveryIsRejectedWhileTheClosureIsClosed() {
        Setup setup = setup("74000005", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadItem(setup, GradeItemType.EXAM, 1, 3.0);
        entityManager.flush();
        publishExam(setup, 1);
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, setup.instructor().getPerson().getId());

        assertThatThrownBy(() -> examRecoveryRecorder.record(exam.getId(), setup.instructor().getId(),
                setup.instructor().getPerson().getId(), new RecordExamRecoveryRequest(Grade.of(BigDecimal.valueOf(8.0)))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void h9CorrectionAfterTermEndIsAllowedWhenTheClosureWasReopened() {
        Setup setup = setup("74000007", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadItem(setup, GradeItemType.EXAM, 1, 3.0);
        entityManager.flush();
        publishExam(setup, 1);
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.REOPENED, setup.instructor().getPerson().getId());
        MutableClock.INSTANCE.setDate(LocalDate.of(3000, 1, 1));

        assertThatCode(() -> publishedGradeCorrector.correct(exam.getId(),
                new CorrectGradeItemRequest(Grade.of(BigDecimal.valueOf(6.0)), "Error de carga detectado en la revision", null),
                setup.enrollment().getStudent().getPerson().getId())).doesNotThrowAnyException();
    }

    @Test
    void h9CorrectionOfAPublishedGradeIsRejectedWhileTheClosureIsClosed() {
        Setup setup = setup("74000015", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadItem(setup, GradeItemType.EXAM, 1, 3.0);
        entityManager.flush();
        publishExam(setup, 1);
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, setup.instructor().getPerson().getId());

        assertThatThrownBy(() -> publishedGradeCorrector.correct(exam.getId(),
                new CorrectGradeItemRequest(Grade.of(BigDecimal.valueOf(6.0)), "Error de carga detectado en la revision", null),
                setup.enrollment().getStudent().getPerson().getId()))
                .isInstanceOf(SectionClosureClosedException.class);
    }

    @Test
    void h9CorrectionAfterTermEndIsRejectedWhenThereIsNoReopenedClosure() {
        Setup setup = setup("74000017", 2, 0, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadItem(setup, GradeItemType.EXAM, 1, 3.0);
        entityManager.flush();
        publishExam(setup, 1);
        MutableClock.INSTANCE.setDate(LocalDate.of(3000, 1, 1));

        assertThatThrownBy(() -> publishedGradeCorrector.correct(exam.getId(),
                new CorrectGradeItemRequest(Grade.of(BigDecimal.valueOf(6.0)), "Error de carga detectado en la revision", null),
                setup.enrollment().getStudent().getPerson().getId()))
                .isInstanceOf(GradeCorrectionPeriodEndedException.class);
    }

    @Test
    void h3FinalGradeFromAnExamBoardIsStillAcceptedWhileTheClosureIsClosed() {
        Setup setup = setup("74000019", 1, 0, true, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(setup, LocalDateTime.now().minusHours(2), null);
        register(board, setup.enrollment());
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, setup.instructor().getPerson().getId());

        assertThatCode(() -> loadFinal(setup, board, 5.0, null)).doesNotThrowAnyException();
    }

    @Test
    @Disabled("E34-A-S2")
    void h2ExamSequenceBeyondTheSchemeExamCountIsRejected() {
        Setup setup = setup("74000009", 2, 0, false, EnrollmentStatus.IN_PROGRESS);

        assertThatThrownBy(() -> loadItem(setup, GradeItemType.EXAM, 5, 7.0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S2")
    void h2CourseworkItemIsRejectedWhenTheSchemeExpectsNone() {
        Setup setup = setup("74000011", 2, 0, false, EnrollmentStatus.IN_PROGRESS);

        assertThatThrownBy(() -> loadItem(setup, GradeItemType.TP, 1, 7.0)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S7")
    void h12FinalGradeOfAClosedExamBoardCanBeCorrectedWithAReason() {
        Setup setup = setup("74000013", 1, 0, true, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(setup, LocalDateTime.now().minusHours(2), null);
        register(board, setup.enrollment());
        loadFinal(setup, board, 5.0, null);
        entityManager.flush();
        examBoardManager.close(board.getId());
        entityManager.flush();

        assertThatCode(() -> loadFinal(setup, board, 8.0, "Error de transcripcion en el acta"))
                .doesNotThrowAnyException();
    }

    @Test
    @Disabled("E34-A-S6")
    void h7WithdrawingFromAnExamBoardIsRejectedOnceTheFinalGradeIsLoaded() {
        Setup setup = setup("74000015", 1, 0, true, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(setup, LocalDateTime.now().minusHours(2), null);
        ExamBoardRegistration registration = register(board, setup.enrollment());
        loadFinal(setup, board, 5.0, null);
        entityManager.flush();

        assertThatThrownBy(() -> examBoardRegistrationManager.withdraw(registration.getId())).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S7")
    void h16RegistrationIsRejectedAfterTheExamDateWhenRegistrationEndIsNull() {
        Setup setup = setup("74000017", 1, 0, true, EnrollmentStatus.REGULAR);
        ExamBoard board = scheduledBoard(setup, LocalDateTime.now().minusDays(3), null);

        assertThatThrownBy(() -> examBoardRegistrationManager.registerProgramEnrollment(board.getId(), setup.enrollment().getId()))
                .isInstanceOf(ExamBoardRegistrationWindowClosedException.class);
    }

    @Test
    @Disabled("E34-A-S7")
    void h16ExamBoardCreationRejectsRegistrationStartAfterRegistrationEnd() {
        Setup setup = setup("74000019", 1, 0, true, EnrollmentStatus.REGULAR);
        Term term = setup.section().getTerm();
        LocalDateTime examDate = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
        while (examDate.getDayOfWeek().getValue() > 5) {
            examDate = examDate.plusDays(1);
        }

        CreateExamBoardRequest request = new CreateExamBoardRequest(setup.section().getSubject().getId(), null, term.getId(),
                examDate, examDate.minusDays(2), examDate.minusDays(10), "Aula 1");

        assertThatThrownBy(() -> examBoardManager.create(request)).isInstanceOf(RuntimeException.class);
    }
}
