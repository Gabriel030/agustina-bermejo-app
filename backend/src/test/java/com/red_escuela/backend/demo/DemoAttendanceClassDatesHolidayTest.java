package com.red_escuela.backend.demo;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAttendanceClassDatesHolidayTest {

    private static final int CLASSES_PER_SECTION = 4;

    private static Set<LocalDate> holidaysOfYears(int firstYear, int lastYear) {
        Set<LocalDate> holidays = new HashSet<>();
        for (int year = firstYear; year <= lastYear; year++) {
            holidays.add(LocalDate.of(year, 1, 1));
            holidays.add(LocalDate.of(year, 3, 24));
            holidays.add(LocalDate.of(year, 4, 2));
            holidays.add(LocalDate.of(year, 5, 1));
            holidays.add(LocalDate.of(year, 5, 25));
            holidays.add(LocalDate.of(year, 6, 20));
            holidays.add(LocalDate.of(year, 7, 9));
            holidays.add(LocalDate.of(year, 12, 8));
            holidays.add(LocalDate.of(year, 12, 25));
            for (int day = 1; day <= 7; day++) {
                holidays.add(LocalDate.of(year, 2, 12 + day));
                holidays.add(LocalDate.of(year, 11, 18 + day));
            }
        }
        return holidays;
    }

    @Test
    void demoSeedNeverCreatesAClassSessionOnAHolidayForAnyFixedClockDate() {
        Set<LocalDate> holidays = holidaysOfYears(2025, 2029);

        for (LocalDate today = LocalDate.of(2026, 1, 1); !today.isAfter(LocalDate.of(2028, 12, 31)); today = today.plusDays(1)) {
            List<LocalDate> classDates = DemoAttendanceSeedStep.recentClassDaysBefore(today, CLASSES_PER_SECTION, holidays);

            assertThat(classDates).as("class dates for clock date %s", today).hasSize(CLASSES_PER_SECTION).doesNotHaveDuplicates().isSorted();
            for (LocalDate classDate : classDates) {
                assertThat(classDate).as("class date for clock date %s", today).isBefore(today);
                assertThat(holidays).as("class date %s for clock date %s", classDate, today).doesNotContain(classDate);
                assertThat(classDate.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            }
        }
    }
}
