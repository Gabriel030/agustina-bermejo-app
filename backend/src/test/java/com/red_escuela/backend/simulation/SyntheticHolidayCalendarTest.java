package com.red_escuela.backend.simulation;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticHolidayCalendarTest {

    @Test
    void sameSeedAndYearGiveTheSameHolidays() {
        assertThat(new SyntheticHolidayCalendar(7).holidaysOf(2027)).isEqualTo(new SyntheticHolidayCalendar(7).holidaysOf(2027));
    }

    @Test
    void differentSeedsGiveDifferentBridgeDays() {
        assertThat(new SyntheticHolidayCalendar(7).holidaysOf(2027)).isNotEqualTo(new SyntheticHolidayCalendar(8).holidaysOf(2027));
    }

    @Test
    void everyBridgeDayFallsOnAWeekdayInsideTheYear() {
        new SyntheticHolidayCalendar(11).holidaysOf(2028).stream()
                .filter(holiday -> holiday.name().startsWith("Feriado puente"))
                .forEach(holiday -> {
                    assertThat(holiday.date().getYear()).isEqualTo(2028);
                    assertThat(holiday.date().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                });
    }

    @Test
    void holidaysAreNotClassDaysAndWeekendsAreNotEither() {
        SyntheticHolidayCalendar calendar = new SyntheticHolidayCalendar(7);
        assertThat(calendar.isClassDay(LocalDate.of(2026, 5, 1))).isFalse();
        assertThat(calendar.isClassDay(LocalDate.of(2026, 3, 7))).isFalse();
        assertThat(calendar.isClassDay(LocalDate.of(2026, 3, 3))).isTrue();
    }

    @Test
    void everyYearHasTheSameCountOfHolidays() {
        SyntheticHolidayCalendar calendar = new SyntheticHolidayCalendar(3);
        assertThat(calendar.holidaysOf(2026)).hasSameSizeAs(calendar.holidaysOf(2031));
    }
}
