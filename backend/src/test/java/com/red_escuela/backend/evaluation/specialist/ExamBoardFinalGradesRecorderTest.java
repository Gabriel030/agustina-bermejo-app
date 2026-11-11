package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardFinalResult;
import com.red_escuela.backend.evaluation.entity.ExamBoardPriorCondition;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardFinalGradeRowRejectedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotYetTakenException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardFinalGradeEntryException;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamBoardFinalGradesRecorderTest extends ExamBoardChangeFixture {

    @Autowired
    private ExamBoardFinalGradesRecorder finalGradesRecorder;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private ExamBoardManager examBoardManager;

    @Autowired
    private GradeItemAuditRepository auditRepository;

    private record Row(SubjectSectionEnrollment enrollment, ExamBoardRegistration registration) {
    }

    private record Board(ExamBoard board, Instructor tribunal, Subject subject, Term term) {
    }

    private Board board(String dni, LocalDateTime examDate) {
        return board(dni, examDate, null);
    }

    private Board board(String dni, LocalDateTime examDate, Integer maxFinalExamAttempts) {
        Subject subject = subject("Materia mesa " + dni, maxFinalExamAttempts);
        Term term = term();
        ExamBoard board = examBoard(subject, term, examDate);
        Instructor tribunal = tribunalMember(board, dni);
        return new Board(board, tribunal, subject, term);
    }

    private Row row(Board board, String dni) {
        SubjectSectionEnrollment enrollment = regularEnrollment(board.subject(), board.term(), dni, "Carrera " + dni);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + dni);
        scheme.setExamCountExpected(0);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        enrollment.getSubjectSection().setEvaluationScheme(scheme);
        ExamBoardRegistration registration = registration(board.board(), enrollment);
        entityManager.flush();
        return new Row(enrollment, registration);
    }

    private ExamBoardFinalGradeEntryRequest score(Row row, String score, String reason) {
        return new ExamBoardFinalGradeEntryRequest(row.registration().getId(), Grade.of(new BigDecimal(score)), null, reason);
    }

    private ExamBoardFinalGradeEntryRequest absent(Row row, String reason) {
        return new ExamBoardFinalGradeEntryRequest(row.registration().getId(), null, true, reason);
    }

    private ExamBoardFinalGradesResponse record(Board board, ExamBoardFinalGradeEntryRequest... entries) {
        return finalGradesRecorder.record(board.board().getId(), board.tribunal().getId(), List.of(entries));
    }

    private List<GradeItem> items(Row row) {
        entityManager.flush();
        return gradeItemRecorder.listByProgramEnrollment(row.enrollment().getId());
    }

    @Test
    void batchLoadsDecimalScoresForManyStudentsAndReportsTheResultAgainstTheThreshold() {
        Board board = board("74000001", LocalDateTime.now().minusDays(1));
        Row passing = row(board, "74000002");
        Row failing = row(board, "74000003");

        ExamBoardFinalGradesResponse response = record(board, score(passing, "7.50", null), score(failing, "5.5", null));

        assertThat(response.registrations()).extracting(ExamBoardRegistrationSummaryResponse::result)
                .containsExactly(ExamBoardFinalResult.PASSED, ExamBoardFinalResult.FAILED);
        assertThat(response.registrations()).extracting(summary -> summary.minApprovalGrade().value())
                .allSatisfy(min -> assertThat(min).isEqualByComparingTo("6"));
        assertThat(response.registrations().get(0).finalScore()).isEqualByComparingTo("7.5");
        assertThat(response.registrations().get(0).gradeItemId()).isEqualTo(items(passing).get(0).getId());
        assertThat(response.registrations().get(0).priorCondition()).isEqualTo(ExamBoardPriorCondition.REGULAR);
        assertThat(items(passing).get(0).getDate()).isEqualTo(board.board().getExamDate());
        assertThat(items(failing)).hasSize(1);
    }

    @Test
    void absentIsPersistedBeforeClosingAndCanBeReversedWithAScore() {
        Board board = board("74000011", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000012");

        record(board, absent(student, null));
        entityManager.flush();
        assertThat(student.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.ABSENT);
        assertThat(items(student)).isEmpty();

        ExamBoardFinalGradesResponse response = record(board, score(student, "8", null));

        assertThat(student.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
        assertThat(response.registrations().get(0).result()).isEqualTo(ExamBoardFinalResult.PASSED);
        assertThat(items(student)).hasSize(1);
    }

    @Test
    void markingAbsentAStudentWithAScoreNeedsAReasonAndRemovesTheGradeWithAnAudit() {
        Board board = board("74000021", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000022");
        record(board, score(student, "7", null));

        assertThatThrownBy(() -> record(board, absent(student, null)))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class)
                .satisfies(failure -> assertThat(((ExamBoardFinalGradeRowRejectedException) failure).details())
                        .containsEntry("registrationId", student.registration().getId())
                        .containsEntry("causeCode", "INVALID_GRADE_CORRECTION_REASON"));
        assertThat(items(student)).hasSize(1);

        record(board, absent(student, "Cargada por error"));

        assertThat(items(student)).isEmpty();
        assertThat(student.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.ABSENT);
        List<GradeItemAudit> audits = auditRepository.findAll();
        assertThat(audits).filteredOn(audit -> audit.getKind() == GradeItemAuditKind.FINAL_REVERT)
                .anySatisfy(audit -> {
                    assertThat(audit.getNewScore()).isNull();
                    assertThat(audit.getReason()).isEqualTo("Cargada por error");
                });
    }

    @Test
    void correctingAScoreInTheSameBoardReusesTheItemAndNeedsAReason() {
        Board board = board("74000031", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000032");
        record(board, score(student, "4", null));
        Long itemId = items(student).get(0).getId();

        assertThatThrownBy(() -> record(board, score(student, "7", null)))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class);
        record(board, score(student, "7", "Error de tipeo"));

        assertThat(items(student)).hasSize(1);
        assertThat(items(student).get(0).getId()).isEqualTo(itemId);
        assertThat(items(student).get(0).getScore().value()).isEqualByComparingTo("7");
    }

    @Test
    void secondAttemptInANewBoardGetsTheNextSequenceAndKeepsTheFirstAttempt() {
        Board first = board("74000041", LocalDateTime.now().minusDays(10), 3);
        Row student = row(first, "74000042");
        record(first, score(student, "4", null));
        first.board().setStatus(ExamBoardStatus.CLOSED);
        student.registration().setStatus(ExamBoardRegistrationStatus.GRADED);
        assertThat(student.enrollment().getStatus()).isEqualTo(com.red_escuela.backend.enrollment.entity.EnrollmentStatus.REGULAR);
        ExamBoard second = examBoard(first.subject(), first.term(), LocalDateTime.now().minusDays(1));
        ExamBoardRegistration secondRegistration = registration(second, student.enrollment());
        Board secondBoard = new Board(second, tribunalMember(second, "74000043"), first.subject(), first.term());

        record(secondBoard, new ExamBoardFinalGradeEntryRequest(secondRegistration.getId(), Grade.of(new BigDecimal("8")), null, null));

        List<GradeItem> finals = items(student);
        assertThat(finals).extracting(GradeItem::getSequenceNumber).containsExactlyInAnyOrder(1, 2);
        assertThat(finals).filteredOn(item -> item.getSequenceNumber() == 1)
                .singleElement().satisfies(item -> assertThat(item.getScore().value()).isEqualByComparingTo("4"));
        assertThat(finals).filteredOn(item -> item.getSequenceNumber() == 2)
                .singleElement().satisfies(item -> assertThat(item.getExamBoard().getId()).isEqualTo(second.getId()));
    }

    @Test
    void closingTheBoardLeavesGradedWhoHasAScoreAndAbsentWhoDoesNot() {
        Board board = board("74000051", LocalDateTime.now().minusDays(1));
        Row graded = row(board, "74000052");
        Row missing = row(board, "74000053");
        Row markedAbsent = row(board, "74000054");
        record(board, score(graded, "6", null), absent(markedAbsent, null));

        examBoardManager.close(board.board().getId());

        assertThat(graded.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.GRADED);
        assertThat(missing.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.ABSENT);
        assertThat(markedAbsent.registration().getStatus()).isEqualTo(ExamBoardRegistrationStatus.ABSENT);
        assertThat(items(missing)).isEmpty();
    }

    @Test
    void aBoardThatIsNotYetTakenRejectsTheLoadAndSaysWhen() {
        Board board = board("74000061", LocalDateTime.now().plusDays(2));
        Row student = row(board, "74000062");

        assertThatThrownBy(() -> record(board, score(student, "7", null)))
                .isInstanceOf(ExamBoardNotYetTakenException.class)
                .satisfies(failure -> assertThat(((ExamBoardNotYetTakenException) failure).details())
                        .containsKey("examDate"));
        assertThat(items(student)).isEmpty();
    }

    @Test
    void theSingleGradePathAlsoRejectsAFinalBeforeTheExamDate() {
        Board board = board("74000063", LocalDateTime.now().plusDays(2));
        Row student = row(board, "74000064");

        assertThatThrownBy(() -> gradeItemRecorder.loadProgramEnrollmentGrade(
                student.enrollment().getId(), board.tribunal().getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), new BigDecimal("7"), null,
                        board.board().getId(), null)))
                .isInstanceOf(ExamBoardNotYetTakenException.class);
    }

    @Test
    void closedBoardRejectsTheLoad() {
        Board board = board("74000071", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000072");
        board.board().setStatus(ExamBoardStatus.CLOSED);
        entityManager.flush();

        assertThatThrownBy(() -> record(board, score(student, "7", null)))
                .isInstanceOf(ExamBoardClosedException.class);
    }

    @Test
    void aTeacherOutsideTheTribunalCannotLoad() {
        Board board = board("74000081", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000082");
        Board other = board("74000083", LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> finalGradesRecorder.record(
                board.board().getId(), other.tribunal().getId(), List.of(score(student, "7", null))))
                .isInstanceOf(NotExamBoardTribunalMemberException.class);
    }

    @Test
    void aWithdrawnRegistrationRejectsTheRowAndIdentifiesIt() {
        Board board = board("74000091", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000092");
        student.registration().setStatus(ExamBoardRegistrationStatus.WITHDRAWN);
        entityManager.flush();

        assertThatThrownBy(() -> record(board, score(student, "7", null)))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class)
                .satisfies(failure -> assertThat(((ExamBoardFinalGradeRowRejectedException) failure).details())
                        .containsEntry("registrationId", student.registration().getId())
                        .containsEntry("causeCode", "EXAM_BOARD_REGISTRATION_WITHDRAWN"));
    }

    @Test
    void aRegistrationOfAnotherBoardRejectsTheRow() {
        Board board = board("74000101", LocalDateTime.now().minusDays(1));
        Board other = board("74000102", LocalDateTime.now().minusDays(1));
        Row foreign = row(other, "74000103");

        assertThatThrownBy(() -> record(board, score(foreign, "7", null)))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class)
                .satisfies(failure -> assertThat(((ExamBoardFinalGradeRowRejectedException) failure).details())
                        .containsEntry("causeCode", "EXAM_BOARD_REGISTRATION_NOT_FOUND"));
    }

    @Test
    void theSameRegistrationTwiceInTheBatchIsRejected() {
        Board board = board("74000111", LocalDateTime.now().minusDays(1));
        Row student = row(board, "74000112");

        assertThatThrownBy(() -> record(board, score(student, "7", null), score(student, "8", null)))
                .isInstanceOf(InvalidExamBoardFinalGradeEntryException.class);
    }

    @Test
    void anEntryNeedsEitherAScoreOrTheAbsentMark() {
        assertThatThrownBy(() -> new ExamBoardFinalGradeEntryRequest(1L, Grade.of(new BigDecimal("7")), true, null))
                .isInstanceOf(InvalidExamBoardFinalGradeEntryException.class);
        assertThatThrownBy(() -> new ExamBoardFinalGradeEntryRequest(1L, null, null, null))
                .isInstanceOf(InvalidExamBoardFinalGradeEntryException.class);
    }
}
