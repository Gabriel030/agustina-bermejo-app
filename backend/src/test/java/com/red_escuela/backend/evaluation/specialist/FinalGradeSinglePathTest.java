package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.FinalGradeRequiresExamBoardException;
import com.red_escuela.backend.evaluation.exception.InvalidGradeCorrectionReasonException;
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
class FinalGradeSinglePathTest extends ExamBoardChangeFixture {

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private ExamBoardManager examBoardManager;

    @Autowired
    private GradeItemAuditRepository auditRepository;

    private record Scenario(SubjectSectionEnrollment enrollment, Subject subject, Term term) {
    }

    private Scenario scenario(String dni) {
        Subject subject = subject("Materia final " + dni, null);
        Term term = term();
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, dni, "Carrera " + dni);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema con final");
        scheme.setExamCountExpected(0);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        enrollment.getSubjectSection().setEvaluationScheme(scheme);
        return new Scenario(enrollment, subject, term);
    }

    private CreateGradeItemRequest finalRequest(double score, Long examBoardId, String reason) {
        return new CreateGradeItemRequest(
                GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(score), null, examBoardId, reason);
    }

    private GradeItem loadFinal(Scenario s, Instructor member, double score, Long examBoardId, String reason) {
        return gradeItemRecorder.loadProgramEnrollmentGrade(
                s.enrollment().getId(), member.getId(), finalRequest(score, examBoardId, reason));
    }

    private ExamBoard boardWithRegistration(Scenario s, Instructor[] memberHolder, String memberDni) {
        ExamBoard board = examBoard(s.subject(), s.term(), LocalDateTime.now().minusDays(1));
        memberHolder[0] = tribunalMember(board, memberDni);
        registration(board, s.enrollment());
        entityManager.flush();
        return board;
    }

    @Test
    void finalWithoutExamBoardIsRejectedAndNothingIsSaved() {
        Scenario s = scenario("73000001");
        Instructor member = tribunalMember(examBoard(s.subject(), s.term(), LocalDateTime.now().plusDays(2)), "73000002");
        entityManager.flush();

        assertThatThrownBy(() -> loadFinal(s, member, 7, null, null))
                .isInstanceOf(FinalGradeRequiresExamBoardException.class);

        assertThat(gradeItemRecorder.listByProgramEnrollment(s.enrollment().getId())).isEmpty();
    }

    @Test
    void finalWithoutExamBoardIsRejectedForCourseEnrollmentsToo() {
        Instructor member = tribunalMember(
                examBoard(subject("Materia curso", null), term(), LocalDateTime.now().plusDays(2)), "73000003");
        entityManager.flush();

        assertThatThrownBy(() -> gradeItemRecorder.loadCourseEnrollmentGrade(
                999_999L, member.getId(), finalRequest(7, null, null)))
                .isInstanceOf(FinalGradeRequiresExamBoardException.class);
    }

    @Test
    void finalLoadedFromTheExamBoardStillWorksAndMovesTheRegistrationToGradedOnClose() {
        Scenario s = scenario("73000011");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000012");

        GradeItem item = loadFinal(s, member[0], 7, board.getId(), null);
        examBoardManager.close(board.getId());

        assertThat(item.getExamBoard().getId()).isEqualTo(board.getId());
        assertThat(examBoardManager.listRegistrants(board.getId()))
                .extracting(ExamBoardRegistration::getStatus)
                .containsExactly(ExamBoardRegistrationStatus.GRADED);
    }

    @Test
    void absentStudentOnCloseStaysAbsentAndConsumesNoFinalAttempt() {
        Scenario s = scenario("73000021");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000022");

        examBoardManager.close(board.getId());

        assertThat(examBoardManager.listRegistrants(board.getId()))
                .extracting(ExamBoardRegistration::getStatus)
                .containsExactly(ExamBoardRegistrationStatus.ABSENT);
        assertThat(gradeItemRecorder.listByProgramEnrollment(s.enrollment().getId())).isEmpty();
    }

    @Test
    void changingAnExistingFinalRecordsAnAuditEntryWithWhoWhenAndWhy() {
        Scenario s = scenario("73000031");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000032");
        GradeItem first = loadFinal(s, member[0], 3, board.getId(), null);
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);

        loadFinal(s, member[0], 8, board.getId(), "  Error de carga en el acta  ");
        entityManager.flush();

        List<GradeItemAudit> audits = auditRepository.findByGradeItemIdOrderByChangedAtDescIdDesc(first.getId());
        assertThat(audits).hasSize(1);
        GradeItemAudit audit = audits.get(0);
        assertThat(audit.getKind()).isEqualTo(GradeItemAuditKind.FINAL_REVERT);
        assertThat(audit.getReason()).isEqualTo("Error de carga en el acta");
        assertThat(audit.getChangedById()).isEqualTo(member[0].getPerson().getId());
        assertThat(audit.getChangedAt()).isAfter(before);
        assertThat(audit.getPreviousScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3)));
        assertThat(audit.getNewScore()).isEqualTo(Grade.of(BigDecimal.valueOf(8)));
        assertThat(audit.getEnrollmentId()).isEqualTo(s.enrollment().getId());
    }

    @Test
    void changingAnExistingFinalWithoutReasonIsRejectedAndKeepsTheScore() {
        Scenario s = scenario("73000041");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000042");
        GradeItem first = loadFinal(s, member[0], 3, board.getId(), null);
        entityManager.flush();

        assertThatThrownBy(() -> loadFinal(s, member[0], 8, board.getId(), null))
                .isInstanceOf(InvalidGradeCorrectionReasonException.class);
        assertThatThrownBy(() -> loadFinal(s, member[0], 8, board.getId(), "  abc "))
                .isInstanceOf(InvalidGradeCorrectionReasonException.class);

        assertThat(gradeItemRecorder.get(first.getId()).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3)));
        assertThat(auditRepository.findByGradeItemIdOrderByChangedAtDescIdDesc(first.getId())).isEmpty();
    }

    @Test
    void resubmittingTheSameFinalScoreRecordsNoAuditAndNeedsNoReason() {
        Scenario s = scenario("73000051");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000052");
        GradeItem first = loadFinal(s, member[0], 3, board.getId(), null);

        loadFinal(s, member[0], 3, board.getId(), null);
        entityManager.flush();

        assertThat(auditRepository.findByGradeItemIdOrderByChangedAtDescIdDesc(first.getId())).isEmpty();
    }

    @Test
    void finalIsLockedAfterTheExamBoardIsClosed() {
        Scenario s = scenario("73000061");
        Instructor[] member = new Instructor[1];
        ExamBoard board = boardWithRegistration(s, member, "73000062");
        GradeItem first = loadFinal(s, member[0], 3, board.getId(), null);
        examBoardManager.close(board.getId());
        entityManager.flush();

        assertThatThrownBy(() -> loadFinal(s, member[0], 9, board.getId(), "Correccion tardia del acta"))
                .isInstanceOf(ExamBoardClosedException.class);

        assertThat(gradeItemRecorder.get(first.getId()).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3)));
        assertThat(auditRepository.findByGradeItemIdOrderByChangedAtDescIdDesc(first.getId())).isEmpty();
    }

    @Test
    void finalOfAClosedBoardCannotBeOverwrittenThroughAnotherOpenBoard() {
        Scenario s = scenario("73000071");
        Instructor[] member = new Instructor[1];
        ExamBoard closedBoard = boardWithRegistration(s, member, "73000072");
        GradeItem first = loadFinal(s, member[0], 3, closedBoard.getId(), null);
        examBoardManager.close(closedBoard.getId());
        ExamBoard openBoard = examBoard(s.subject(), s.term(), LocalDateTime.now().minusHours(2));
        Instructor secondMember = tribunalMember(openBoard, "73000073");
        registration(openBoard, s.enrollment());
        entityManager.flush();

        assertThatThrownBy(() -> loadFinal(s, secondMember, 9, openBoard.getId(), "Intento de pisar el final"))
                .isInstanceOf(ExamBoardClosedException.class);

        assertThat(gradeItemRecorder.get(first.getId()).getScore()).isEqualTo(Grade.of(BigDecimal.valueOf(3)));
        assertThat(s.enrollment().getStatus()).isNotEqualTo(EnrollmentStatus.APPROVED);
    }
}
