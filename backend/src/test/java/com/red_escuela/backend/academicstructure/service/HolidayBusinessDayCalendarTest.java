package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("c07")
@Tag("D-321")
class HolidayBusinessDayCalendarTest {
    private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
    private final HolidayBusinessDayCalendar calendar = new HolidayBusinessDayCalendar(holidayRepository);

    @ParameterizedTest
    @ValueSource(strings = {"2027-03-08", "2027-03-09", "2027-03-10", "2027-03-11", "2027-03-12"})
    void aWeekdayWithoutHolidayIsABusinessDay(String date) {
        assertThat(calendar.isBusinessDay(LocalDate.parse(date))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-03-06", "2027-03-07"})
    void saturdayAndSundayAreNotBusinessDays(String date) {
        assertThat(calendar.isBusinessDay(LocalDate.parse(date))).isFalse();
        verify(holidayRepository, never()).existsByDate(LocalDate.parse(date));
    }

    @Test
    void aWeekdayInTheHolidayTableIsNotABusinessDay() {
        LocalDate holiday = LocalDate.of(2027, 3, 24);
        when(holidayRepository.existsByDate(holiday)).thenReturn(true);

        assertThat(calendar.isBusinessDay(holiday)).isFalse();
    }
}
