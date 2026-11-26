package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import com.red_escuela.backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GradeItemExistenceOracleTest extends ExamBoardChangeFixture {

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private SubjectSectionEnrollment enrollment(String dni, Subject subject, Term term, boolean withScheme) {
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, dni, "Carrera " + dni);
        if (withScheme) {
            EvaluationScheme scheme = new EvaluationScheme();
            scheme.setName("Esquema con final");
            scheme.setExamCountExpected(0);
            scheme.setTpCountExpected(0);
            scheme.setHasFinal(true);
            scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
            scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
            scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
            entityManager.persist(scheme);
            enrollment.getSubjectSection().setEvaluationSchemeId(scheme.getId());
        }
        return enrollment;
    }

    private CreateGradeItemRequest finalRequest(Long examBoardId) {
        return new CreateGradeItemRequest(
                GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(7), null, examBoardId, null);
    }

    private ResponseEntity<?> responseFor(Long enrollmentId, Long instructorId, Long examBoardId) {
        entityManager.flush();
        Throwable thrown = catchThrowable(() -> gradeItemRecorder.loadProgramEnrollmentGrade(
                enrollmentId, instructorId, finalRequest(examBoardId)));
        assertThat(thrown).isInstanceOf(NotFoundException.class);
        return handler.handleNotFound((NotFoundException) thrown);
    }

    private void assertIndistinguishable(ResponseEntity<?> actual, ResponseEntity<?> reference) {
        assertThat(actual.getStatusCode().value()).isEqualTo(404);
        assertThat(actual.getStatusCode()).isEqualTo(reference.getStatusCode());
        assertThat(actual.getBody()).isEqualTo(reference.getBody());
        assertThat(actual.getHeaders()).isEqualTo(reference.getHeaders());
    }

    @Test
    void outsiderOnClosedBoardGetsTheSameResponseAsOnMissingBoard() {
        Subject subject = subject("Materia oraculo 1", null);
        Term term = term();
        SubjectSectionEnrollment enrollment = enrollment("73100001", subject, term, true);
        ExamBoard closedBoard = examBoard(subject, term, LocalDateTime.now().minusDays(1));
        closedBoard.setStatus(ExamBoardStatus.CLOSED);
        ExamBoard openBoard = examBoard(subject, term, LocalDateTime.now().minusDays(1));
        tribunalMember(closedBoard, "73100002");
        tribunalMember(openBoard, "73100004");
        Instructor outsider = tribunalMember(examBoard(subject, term, LocalDateTime.now().minusDays(1)), "73100003");

        ResponseEntity<?> missing = responseFor(enrollment.getId(), outsider.getId(), 999_999_999L);

        assertIndistinguishable(responseFor(enrollment.getId(), outsider.getId(), closedBoard.getId()), missing);
        assertIndistinguishable(responseFor(enrollment.getId(), outsider.getId(), openBoard.getId()), missing);
    }

    @Test
    void outsiderOnBoardGetsTheSameResponseAsOnMissingEnrollment() {
        Subject subject = subject("Materia oraculo 2", null);
        Term term = term();
        SubjectSectionEnrollment enrollment = enrollment("73100011", subject, term, true);
        ExamBoard board = examBoard(subject, term, LocalDateTime.now().minusDays(1));
        Instructor outsider = tribunalMember(examBoard(subject, term, LocalDateTime.now().minusDays(1)), "73100012");

        ResponseEntity<?> missing = responseFor(999_999_999L, outsider.getId(), board.getId());

        assertIndistinguishable(responseFor(enrollment.getId(), outsider.getId(), board.getId()), missing);
    }

    @Test
    void memberWithUnregisteredStudentGetsTheSameResponseWhetherOrNotTheSectionHasAScheme() {
        Subject subject = subject("Materia oraculo 3", null);
        Term term = term();
        SubjectSectionEnrollment withScheme = enrollment("73100021", subject, term, true);
        SubjectSectionEnrollment withoutScheme = enrollment("73100022", subject, term, false);
        ExamBoard board = examBoard(subject, term, LocalDateTime.now().minusDays(1));
        Instructor member = tribunalMember(board, "73100023");

        ResponseEntity<?> reference = responseFor(withScheme.getId(), member.getId(), board.getId());

        assertIndistinguishable(responseFor(withoutScheme.getId(), member.getId(), board.getId()), reference);
    }
}
