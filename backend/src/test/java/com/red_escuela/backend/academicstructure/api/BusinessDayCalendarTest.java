package com.red_escuela.backend.academicstructure.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-510")
class BusinessDayCalendarTest {
    private static final LocalDate MONDAY = LocalDate.of(2038, 5, 31);

    private static BusinessDayCalendar calendarWithHolidays(LocalDate... holidays) {
        Set<LocalDate> closed = Set.of(holidays);
        return day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !closed.contains(day);
    }

    @Test
    void theFirstBusinessDayOnOrBeforeABusinessDayIsThatSameDay() {
        assertThat(calendarWithHolidays().nthBusinessDayOnOrBefore(MONDAY, 1)).isEqualTo(MONDAY);
    }

    @Test
    void theFirstBusinessDayOnOrBeforeASaturdayIsThePreviousFriday() {
        assertThat(calendarWithHolidays().nthBusinessDayOnOrBefore(LocalDate.of(2038, 5, 29), 1)).isEqualTo(LocalDate.of(2038, 5, 28));
    }

    @Test
    void theEleventhBusinessDayCountingTheReferenceItselfSkipsWeekends() {
        assertThat(calendarWithHolidays().nthBusinessDayOnOrBefore(MONDAY, 11)).isEqualTo(LocalDate.of(2038, 5, 17));
    }

    @Test
    void holidaysAreSkippedWhenCountingBack() {
        assertThat(calendarWithHolidays(LocalDate.of(2038, 5, 17)).nthBusinessDayOnOrBefore(MONDAY, 11)).isEqualTo(LocalDate.of(2038, 5, 14));
    }

    @Test
    void thePositionMustBeAtLeastOne() {
        assertThatThrownBy(() -> calendarWithHolidays().nthBusinessDayOnOrBefore(MONDAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
