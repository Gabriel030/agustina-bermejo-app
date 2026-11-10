package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GradeItemExamBoardScopeTest extends ExamBoardChangeFixture {

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    private record Scenario(SubjectSectionEnrollment enrollment, Subject subject, Term term) {
    }

    private Scenario scenario(String dni) {
        Subject subject = subject("Materia mesa " + dni, null);
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

    private CreateGradeItemRequest request(GradeItemType type, Long examBoardId) {
        return new CreateGradeItemRequest(
                type, 1, null, LocalDateTime.now(), BigDecimal.valueOf(7), null, examBoardId, null);
    }

    private void withdrawnRegistration(ExamBoard board, SubjectSectionEnrollment enrollment) {
        ExamBoardRegistration registration = registration(board, enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.WITHDRAWN);
    }

    private void assertRejectedWithoutSaving(Scenario s, Instructor member, CreateGradeItemRequest request) {
        entityManager.flush();
        assertThatThrownBy(() -> gradeItemRecorder.loadProgramEnrollmentGrade(
                s.enrollment().getId(), member.getId(), request))
                .isInstanceOf(NotOwnSectionException.class);
        assertThat(gradeItemRecorder.listByProgramEnrollment(s.enrollment().getId())).isEmpty();
    }

    @Test
    void tribunalMemberLoadsTheFinalOfAStudentRegisteredInTheBoard() {
        Scenario s = scenario("72100001");
        ExamBoard board = examBoard(s.subject(), s.term(), LocalDateTime.now().plusDays(2));
        Instructor member = tribunalMember(board, "72100002");
        registration(board, s.enrollment());
        entityManager.flush();

        var item = gradeItemRecorder.loadProgramEnrollmentGrade(
                s.enrollment().getId(), member.getId(), request(GradeItemType.FINAL, board.getId()));

        assertThat(item.getExamBoard().getId()).isEqualTo(board.getId());
    }

    @Test
    void tribunalMemberCannotGradeAStudentNotRegisteredInTheBoard() {
        Scenario s = scenario("72100011");
        ExamBoard board = examBoard(s.subject(), s.term(), LocalDateTime.now().plusDays(2));
        Instructor member = tribunalMember(board, "72100012");

        assertRejectedWithoutSaving(s, member, request(GradeItemType.FINAL, board.getId()));
    }

    @Test
    void tribunalMemberCannotGradeAStudentWhoWithdrewFromTheBoard() {
        Scenario s = scenario("72100021");
        ExamBoard board = examBoard(s.subject(), s.term(), LocalDateTime.now().plusDays(2));
        Instructor member = tribunalMember(board, "72100022");
        withdrawnRegistration(board, s.enrollment());

        assertRejectedWithoutSaving(s, member, request(GradeItemType.FINAL, board.getId()));
    }

    @Test
    void tribunalMemberCannotGradeThroughABoardOfAnotherSubject() {
        Scenario s = scenario("72100031");
        Subject otherSubject = subject("Otra materia", null);
        ExamBoard otherBoard = examBoard(otherSubject, s.term(), LocalDateTime.now().plusDays(2));
        Instructor member = tribunalMember(otherBoard, "72100032");
        registration(otherBoard, s.enrollment());

        assertRejectedWithoutSaving(s, member, request(GradeItemType.FINAL, otherBoard.getId()));
    }

    @Test
    void tribunalMemberCannotLoadANonFinalItemThroughTheBoard() {
        Scenario s = scenario("72100041");
        ExamBoard board = examBoard(s.subject(), s.term(), LocalDateTime.now().plusDays(2));
        Instructor member = tribunalMember(board, "72100042");
        registration(board, s.enrollment());

        assertRejectedWithoutSaving(s, member, request(GradeItemType.EXAM, board.getId()));
    }
}
