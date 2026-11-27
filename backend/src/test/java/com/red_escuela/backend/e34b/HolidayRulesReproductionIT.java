package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.HolidayCreator;
import com.red_escuela.backend.academicstructure.service.HolidayDeleter;
import com.red_escuela.backend.academicstructure.service.HolidayYearGuard;
import com.red_escuela.backend.academicstructure.service.HolidaysMissingForNextYearReminderJob;
import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicy;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayRulesReproductionIT extends ReproductionFixture {

    @Autowired
    private HolidayCreator holidayCreator;
    @Autowired
    private HolidayDeleter holidayDeleter;
    @Autowired
    private HolidayYearGuard holidayYearGuard;
    @Autowired
    private HolidaysMissingForNextYearReminderJob reminderJob;
    @Autowired
    private JustificationDeadlinePolicy justificationDeadlinePolicy;

    private Holiday holiday(LocalDate date) {
        Holiday holiday = new Holiday();
        holiday.setDate(date);
        holiday.setName("Feriado " + date);
        holiday.setType(HolidayType.NATIONAL);
        entityManager.persist(holiday);
        return holiday;
    }

    @Test
    @Disabled("E34-B-P5")
    void holidayCannotBeCreatedOnADateWithAnScheduledExamBoard() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2046, 3, 1, 0, 0), LocalDateTime.of(2046, 7, 1, 0, 0));
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(LocalDateTime.of(2046, 5, 2, 10, 0));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        flushAndClear();

        assertThatThrownBy(() -> holidayCreator.create(
                new CreateHolidayRequest(LocalDate.of(2046, 5, 2), "Feriado puente", HolidayType.NATIONAL, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-B-P5")
    void deletingAHolidayDoesNotChangeTheDeadlineOfAnOpenJustification() {
        LocalDate classDate = LocalDate.of(2046, 3, 5);
        Holiday holiday = holiday(LocalDate.of(2046, 3, 6));
        flushAndClear();
        LocalDate deadlineWithHoliday = justificationDeadlinePolicy.deadlineOf(classDate);

        holidayDeleter.delete(holiday.getId());
        flushAndClear();

        assertThat(justificationDeadlinePolicy.deadlineOf(classDate)).isEqualTo(deadlineWithHoliday);
    }

    @Test
    @Disabled("E34-B-P5")
    void singleHolidayDoesNotMarkTheWholeYearAsLoaded() {
        holiday(LocalDate.of(2045, 1, 1));
        flushAndClear();

        assertThatThrownBy(() -> holidayYearGuard.ensureHolidaysLoaded(2045)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-B-P5")
    void termHolidayCheckUsesTheTermDatesAndNotOnlyItsYearField() {
        holiday(LocalDate.of(2045, 1, 1));
        Term term = term(LocalDateTime.of(2046, 3, 1, 0, 0), LocalDateTime.of(2046, 7, 1, 0, 0));
        term.setYear(2045);
        flushAndClear();

        assertThatThrownBy(() -> holidayYearGuard.ensureHolidaysLoadedForTerm(term)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-B-P5")
    void reminderAlsoCoversTheCurrentYearWhenItsHolidaysAreMissing() {
        boolean reminded = reminderJob.remindIfMissing(LocalDate.of(2046, 1, 10));

        assertThat(reminded).isTrue();
    }
}
