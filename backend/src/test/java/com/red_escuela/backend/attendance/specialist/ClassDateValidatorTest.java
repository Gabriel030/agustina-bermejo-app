package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.exception.ClassDateAfterTermEndException;
import com.red_escuela.backend.attendance.exception.ClassDateBeforeTermStartException;
import com.red_escuela.backend.attendance.exception.ClassDateInFutureException;
import com.red_escuela.backend.attendance.exception.ClassDayWithoutScheduleSlotException;
import com.red_escuela.backend.attendance.exception.ClassOnWeekendException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassDateValidatorTest {

    private static final Long SECTION_ID = 7L;
    private static final LocalDate TERM_START = LocalDate.of(2026, 3, 2);
    private static final LocalDate TERM_END = LocalDate.of(2026, 11, 20);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 15);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 16);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    private final SectionTermEndFinder termFinder = mock(SectionTermEndFinder.class);
    private final SectionScheduleFinder scheduleFinder = mock(SectionScheduleFinder.class);
    private Clock clock = Clock.fixed(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private ClassDateValidator validator() {
        for (SectionKind kind : SectionKind.values()) {
            when(termFinder.termStartDate(kind, SECTION_ID)).thenReturn(TERM_START);
            when(termFinder.termEndDate(kind, SECTION_ID)).thenReturn(TERM_END);
        }
        return new ClassDateValidator(termFinder, scheduleFinder, clock);
    }

    private SectionSlotView slot(DayOfWeek day) {
        return new SectionSlotView(day, LocalTime.of(18, 0), LocalTime.of(20, 0), "Aula 1");
    }

    private void givenProgramSlots(DayOfWeek... days) {
        when(scheduleFinder.findProgramSectionSlots(SECTION_ID)).thenReturn(java.util.Arrays.stream(days).map(this::slot).toList());
    }

    private void givenCourseSlots(DayOfWeek... days) {
        when(scheduleFinder.findCourseSectionSlots(SECTION_ID)).thenReturn(java.util.Arrays.stream(days).map(this::slot).toList());
    }

    @Test
    void saturdayWithoutSlotIsRejectedWithItsOwnCode() {
        givenProgramSlots(DayOfWeek.TUESDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, SATURDAY))
                .isInstanceOf(ClassOnWeekendException.class);
        assertThat(ErrorCodeResponse.codeOf(ClassOnWeekendException.class)).isEqualTo("CLASS_ON_WEEKEND");
    }

    @Test
    void sundayWithoutSlotIsRejectedWithItsOwnCode() {
        givenProgramSlots(DayOfWeek.TUESDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, SUNDAY))
                .isInstanceOf(ClassOnWeekendException.class);
    }

    @Test
    void weekendIsRejectedEvenWhenTheSectionHasNoSlotsAtAll() {
        givenProgramSlots();

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, SATURDAY))
                .isInstanceOf(ClassOnWeekendException.class);
    }

    @Test
    void saturdayWithASaturdaySlotIsAccepted() {
        givenProgramSlots(DayOfWeek.SATURDAY);

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, SATURDAY)).doesNotThrowAnyException();
    }

    @Test
    void dateBeforeTheTermStartIsRejected() {
        givenProgramSlots(DayOfWeek.MONDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, TERM_START.minusDays(7)))
                .isInstanceOf(ClassDateBeforeTermStartException.class);
    }

    @Test
    void dateAfterTheTermEndIsRejected() {
        clock = Clock.fixed(LocalDate.of(2026, 12, 15).atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        givenProgramSlots(DayOfWeek.MONDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, TERM_END.plusDays(3)))
                .isInstanceOf(ClassDateAfterTermEndException.class);
    }

    @Test
    void firstDayOfTheTermIsAccepted() {
        givenProgramSlots(TERM_START.getDayOfWeek());

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, TERM_START)).doesNotThrowAnyException();
    }

    @Test
    void lastDayOfTheTermIsAccepted() {
        clock = Clock.fixed(LocalDate.of(2026, 12, 15).atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        givenProgramSlots(TERM_END.getDayOfWeek());

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, TERM_END)).doesNotThrowAnyException();
    }

    @Test
    void dateAfterTodayAccordingToTheInjectedClockIsRejected() {
        givenProgramSlots(DayOfWeek.THURSDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, TODAY.plusDays(1)))
                .isInstanceOf(ClassDateInFutureException.class);
    }

    @Test
    void todayIsAccepted() {
        givenProgramSlots(WEDNESDAY.getDayOfWeek());

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, TODAY)).doesNotThrowAnyException();
    }

    @Test
    void weekdayWithoutSlotWhenTheSectionHasSlotsOnOtherDaysIsRejected() {
        givenProgramSlots(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);

        assertThatThrownBy(() -> validator().requireValidProgramClassDate(SECTION_ID, TUESDAY))
                .isInstanceOf(ClassDayWithoutScheduleSlotException.class);
    }

    @Test
    void weekdayWithSlotInsideTheTermIsAccepted() {
        givenProgramSlots(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, TUESDAY)).doesNotThrowAnyException();
    }

    @Test
    void weekdayIsAcceptedWhenTheSectionHasNoSlotsLoaded() {
        givenProgramSlots();

        assertThatCode(() -> validator().requireValidProgramClassDate(SECTION_ID, TUESDAY)).doesNotThrowAnyException();
    }

    @Test
    void courseSectionsAreValidatedWithTheSameRules() {
        givenCourseSlots(DayOfWeek.MONDAY);

        assertThatThrownBy(() -> validator().requireValidCourseClassDate(SECTION_ID, TUESDAY))
                .isInstanceOf(ClassDayWithoutScheduleSlotException.class);
        assertThatThrownBy(() -> validator().requireValidCourseClassDate(SECTION_ID, SATURDAY))
                .isInstanceOf(ClassOnWeekendException.class);
        assertThatThrownBy(() -> validator().requireValidCourseClassDate(SECTION_ID, TODAY.plusDays(7)))
                .isInstanceOf(ClassDateInFutureException.class);
        assertThatThrownBy(() -> validator().requireValidCourseClassDate(SECTION_ID, TERM_START.minusDays(7)))
                .isInstanceOf(ClassDateBeforeTermStartException.class);
        assertThatCode(() -> validator().requireValidCourseClassDate(SECTION_ID, LocalDate.of(2026, 9, 14))).doesNotThrowAnyException();
    }

    @Test
    void everyNewExceptionHasADistinctCode() {
        assertThat(List.of(
                ErrorCodeResponse.codeOf(ClassDateInFutureException.class),
                ErrorCodeResponse.codeOf(ClassDateBeforeTermStartException.class),
                ErrorCodeResponse.codeOf(ClassDateAfterTermEndException.class),
                ErrorCodeResponse.codeOf(ClassOnWeekendException.class),
                ErrorCodeResponse.codeOf(ClassDayWithoutScheduleSlotException.class))).doesNotHaveDuplicates();
    }
}
