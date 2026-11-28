package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.api.HolidayAffectedItem;
import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class HolidayRulesReproductionIT extends ReproductionFixture {

    @Autowired
    private HolidayCreator holidayCreator;
    @Autowired
    private com.red_escuela.backend.academicstructure.service.HolidayBusinessDayCalendar holidayBusinessDayCalendar;
    @Autowired
    private com.red_escuela.backend.academicstructure.api.HolidayFinder holidayFinder;
    @Autowired
    private HolidayDeleter holidayDeleter;
    @Autowired
    private HolidayYearGuard holidayYearGuard;
    @Autowired
    private HolidaysMissingForNextYearReminderJob reminderJob;
    @Autowired
    private JustificationDeadlinePolicy justificationDeadlinePolicy;

    private Holiday holiday(LocalDate date) {
        return holiday(date, HolidayScope.BOTH);
    }

    private Holiday holiday(LocalDate date, HolidayScope scope) {
        Holiday holiday = new Holiday();
        holiday.setScope(scope);
        holiday.setDate(date);
        holiday.setName("Feriado " + date);
        holiday.setType(HolidayType.NATIONAL);
        entityManager.persist(holiday);
        return holiday;
    }

    @Test
    void holidayCreatedOnADateWithAScheduledExamBoardReportsTheAffectedBoard() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2046, 3, 1, 0, 0), LocalDateTime.of(2046, 7, 1, 0, 0));
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(LocalDateTime.of(2046, 5, 2, 10, 0));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        flushAndClear();

        HolidayResponse response = holidayCreator.create(
                new CreateHolidayRequest(LocalDate.of(2046, 5, 2), "Feriado puente", HolidayType.NATIONAL, null));

        assertThat(response.affected()).extracting(HolidayAffectedItem::kind, HolidayAffectedItem::referenceId)
                .containsExactly(tuple("EXAM_BOARD", board.getId()));
    }

    @Test
    @org.junit.jupiter.api.Disabled("E34-B-P5 pendiente: plazo de justificacion persistido")
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
    void classesScopedHolidayDoesNotShiftBillingBusinessDaysAndBillingScopedOneDoesNotBlockClasses() {
        LocalDate classesOnly = LocalDate.of(2047, 4, 2);
        LocalDate billingOnly = LocalDate.of(2047, 4, 3);
        holiday(classesOnly, HolidayScope.CLASSES);
        holiday(billingOnly, HolidayScope.BILLING);
        flushAndClear();

        assertThat(holidayBusinessDayCalendar.isBusinessDay(classesOnly)).isTrue();
        assertThat(holidayBusinessDayCalendar.isBusinessDay(billingOnly)).isFalse();
        assertThat(holidayFinder.isHoliday(classesOnly)).isTrue();
        assertThat(holidayFinder.isHoliday(billingOnly)).isFalse();
    }

    @Test
    void singleHolidayDoesNotMarkTheWholeYearAsLoaded() {
        holiday(LocalDate.of(2045, 1, 1));
        flushAndClear();

        assertThatThrownBy(() -> holidayYearGuard.ensureHolidaysLoaded(2045)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void termHolidayCheckUsesTheTermDatesAndNotOnlyItsYearField() {
        holiday(LocalDate.of(2045, 1, 1));
        Term term = term(LocalDateTime.of(2046, 3, 1, 0, 0), LocalDateTime.of(2046, 7, 1, 0, 0));
        term.setYear(2045);
        flushAndClear();

        assertThatThrownBy(() -> holidayYearGuard.ensureHolidaysLoadedForTerm(term)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void reminderAlsoCoversTheCurrentYearWhenItsHolidaysAreMissing() {
        boolean reminded = reminderJob.remindIfMissing(LocalDate.of(2046, 1, 10));

        assertThat(reminded).isTrue();
    }
}
