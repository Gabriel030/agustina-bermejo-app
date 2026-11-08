package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.events.ExamBoardRescheduledEvent;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardHasGradesException;
import com.red_escuela.backend.evaluation.exception.ExamDateOnHolidayException;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ExamBoardReschedulerTest extends ExamBoardChangeFixture {

    private static final LocalDateTime OLD_DATE = LocalDateTime.of(2099, 11, 23, 18, 0);
    private static final LocalDateTime NEW_DATE = LocalDateTime.of(2099, 12, 7, 19, 30);

    @Autowired
    private ExamBoardRescheduler rescheduler;

    @Autowired
    private ApplicationEvents applicationEvents;

    private RescheduleExamBoardRequest requestFor(LocalDateTime date) {
        return new RescheduleExamBoardRequest(date, date.minusDays(10), date.minusDays(1), "Aula 7");
    }

    @Test
    void rescheduleKeepsRegistrationsAndPublishesEvent() {
        Subject subject = subject("MateriaReprogramar", 3);
        Term term = term();
        ExamBoard examBoard = examBoard(subject, term, OLD_DATE);
        tribunalMember(examBoard, "81000001");
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, "81000002", "CarreraReprogramar");
        ExamBoardRegistration registration = registration(examBoard, enrollment);
        entityManager.flush();
        entityManager.refresh(examBoard);

        ExamBoardResponse rescheduled = rescheduler.reschedule(examBoard.getId(), requestFor(NEW_DATE));

        assertThat(rescheduled.examDate()).isEqualTo(NEW_DATE);
        assertThat(rescheduled.rescheduledFrom()).isEqualTo(OLD_DATE);
        assertThat(rescheduled.classroom()).isEqualTo("Aula 7");
        assertThat(registration.getStatus()).isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
        assertThat(applicationEvents.stream(ExamBoardRescheduledEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.oldExamDate()).isEqualTo(OLD_DATE);
            assertThat(event.newExamDate()).isEqualTo(NEW_DATE);
            assertThat(event.registeredStudentPersonIds()).containsExactly(enrollment.getStudent().getPerson().getId());
            assertThat(event.tribunalPersonIds()).hasSize(1);
        });
    }

    @Test
    void rescheduleRejectsHoliday() {
        Holiday holiday = new Holiday();
        holiday.setDate(LocalDate.of(2099, 12, 8));
        holiday.setName("Feriado de prueba");
        holiday.setType(HolidayType.NATIONAL);
        entityManager.persist(holiday);
        ExamBoard examBoard = examBoard(subject("MateriaReprogramarFeriado", 3), term(), OLD_DATE);
        entityManager.flush();

        assertThatThrownBy(() -> rescheduler.reschedule(examBoard.getId(), requestFor(LocalDateTime.of(2099, 12, 8, 18, 0))))
                .isInstanceOf(ExamDateOnHolidayException.class);
    }

    @Test
    void rescheduleRejectsClosedBoard() {
        ExamBoard examBoard = examBoard(subject("MateriaReprogramarCerrada", 3), term(), OLD_DATE);
        examBoard.setStatus(ExamBoardStatus.CLOSED);
        entityManager.flush();

        assertThatThrownBy(() -> rescheduler.reschedule(examBoard.getId(), requestFor(NEW_DATE)))
                .isInstanceOf(ExamBoardClosedException.class);
    }

    @Test
    void rescheduleRejectsBoardWithLoadedGrades() {
        Subject subject = subject("MateriaReprogramarConNotas", 3);
        Term term = term();
        ExamBoard examBoard = examBoard(subject, term, OLD_DATE);
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, "81000003", "CarreraReprogramarConNotas");
        GradeItem finalGrade = new GradeItem();
        finalGrade.setProgramSubjectSectionEnrollment(enrollment);
        finalGrade.setType(GradeItemType.FINAL);
        finalGrade.setSequenceNumber(1);
        finalGrade.setDate(OLD_DATE);
        finalGrade.setScore(Grade.of(BigDecimal.valueOf(7.0)));
        finalGrade.setExamBoard(examBoard);
        entityManager.persist(finalGrade);
        entityManager.flush();

        assertThatThrownBy(() -> rescheduler.reschedule(examBoard.getId(), requestFor(NEW_DATE)))
                .isInstanceOf(ExamBoardHasGradesException.class);
    }
}
