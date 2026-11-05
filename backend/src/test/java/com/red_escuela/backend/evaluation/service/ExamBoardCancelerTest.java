package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.events.ExamBoardCanceledEvent;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardHasGradesException;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ExamBoardCancelerTest extends ExamBoardChangeFixture {

    private static final LocalDateTime EXAM_DATE = LocalDateTime.of(2099, 11, 23, 18, 0);

    @Autowired
    private ExamBoardCanceler canceler;

    @Autowired
    private ExamBoardRegistrationService registrationService;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void cancelWithdrawsAllRegisteredStudents() {
        Subject subject = subject("MateriaCancelar", 3);
        Term term = term();
        ExamBoard examBoard = examBoard(subject, term, EXAM_DATE);
        tribunalMember(examBoard, "82000001");
        ExamBoardRegistration first = registration(examBoard, regularEnrollment(subject, term, "82000002", "CarreraCancelarUno"));
        ExamBoardRegistration second = registration(examBoard, regularEnrollment(subject, term, "82000003", "CarreraCancelarDos"));
        entityManager.flush();
        entityManager.refresh(examBoard);

        ExamBoardResponse canceled = canceler.cancel(examBoard.getId(), "Falta de quórum");

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(canceled.cancelReason()).isEqualTo("Falta de quórum");
        assertThat(canceled.canceledAt()).isNotNull();
        assertThat(first.getStatus()).isEqualTo(ExamBoardRegistrationStatus.WITHDRAWN);
        assertThat(second.getStatus()).isEqualTo(ExamBoardRegistrationStatus.WITHDRAWN);
        assertThat(applicationEvents.stream(ExamBoardCanceledEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.registeredStudentPersonIds()).hasSize(2);
            assertThat(event.tribunalPersonIds()).hasSize(1);
            assertThat(event.reason()).isEqualTo("Falta de quórum");
        });
    }

    @Test
    void canceledRegistrationDoesNotCountAsAttempt() {
        Subject subject = subject("MateriaCancelarIntento", 1);
        Term term = term();
        ExamBoard canceledBoard = examBoard(subject, term, EXAM_DATE);
        ExamBoard otherBoard = examBoard(subject, term, EXAM_DATE.plusDays(14));
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, "82000004", "CarreraCancelarIntento");
        registration(canceledBoard, enrollment);
        entityManager.flush();
        entityManager.refresh(canceledBoard);

        canceler.cancel(canceledBoard.getId(), "Falta de quórum");
        entityManager.flush();
        ExamBoardRegistration retry = registrationService.registerProgramEnrollment(otherBoard.getId(), enrollment.getId());

        assertThat(retry.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void cancelRejectsBoardWithLoadedGrades() {
        Subject subject = subject("MateriaCancelarConNotas", 3);
        Term term = term();
        ExamBoard examBoard = examBoard(subject, term, EXAM_DATE);
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, "82000005", "CarreraCancelarConNotas");
        GradeItem finalGrade = new GradeItem();
        finalGrade.setProgramSubjectSectionEnrollment(enrollment);
        finalGrade.setType(GradeItemType.FINAL);
        finalGrade.setSequenceNumber(1);
        finalGrade.setDate(EXAM_DATE);
        finalGrade.setScore(Grade.of(BigDecimal.valueOf(7.0)));
        finalGrade.setExamBoard(examBoard);
        entityManager.persist(finalGrade);
        entityManager.flush();

        assertThatThrownBy(() -> canceler.cancel(examBoard.getId(), "Falta de quórum"))
                .isInstanceOf(ExamBoardHasGradesException.class);
    }

    @Test
    void cancelRejectsClosedBoard() {
        ExamBoard examBoard = examBoard(subject("MateriaCancelarCerrada", 3), term(), EXAM_DATE);
        examBoard.setStatus(ExamBoardStatus.CLOSED);
        entityManager.flush();

        assertThatThrownBy(() -> canceler.cancel(examBoard.getId(), "Falta de quórum"))
                .isInstanceOf(ExamBoardClosedException.class);
    }
}
