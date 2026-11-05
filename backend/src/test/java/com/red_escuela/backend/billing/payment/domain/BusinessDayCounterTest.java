package com.red_escuela.backend.billing.payment.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-268")
@Tag("D-321")
class BusinessDayCounterTest {

    private static final LocalDate MONDAY = LocalDate.of(2027, 3, 15);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private static BusinessDayCounter withHolidays(LocalDate... holidays) {
        Set<LocalDate> holidaySet = Set.of(holidays);
        return new BusinessDayCounter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidaySet.contains(day));
    }

    @ParameterizedTest
    @CsvSource({
            "2027-03-15,2027-03-15,0",
            "2027-03-15,2027-03-16,1",
            "2027-03-15,2027-03-17,2",
            "2027-03-15,2027-03-18,3",
            "2027-03-15,2027-03-19,4",
            "2027-03-15,2027-03-20,4",
            "2027-03-15,2027-03-21,4",
            "2027-03-15,2027-03-22,5",
            "2027-03-19,2027-03-22,1",
            "2027-03-20,2027-03-22,1",
            "2027-03-21,2027-03-22,1",
            "2027-03-20,2027-03-21,0",
            "2027-03-16,2027-03-15,0"})
    void countsOnlyWeekdaysStrictlyAfterTheStartDate(LocalDate start, LocalDate end, int expected) {
        assertThat(withHolidays().countBusinessDaysAfter(start, end)).isEqualTo(expected);
    }

    @Test
    void aHolidayInTheMiddleDoesNotCount() {
        BusinessDayCounter counter = withHolidays(TUESDAY);

        assertThat(counter.countBusinessDaysAfter(MONDAY, MONDAY.plusDays(3))).isEqualTo(2);
        assertThat(counter.countBusinessDaysAfter(MONDAY, MONDAY.plusDays(4))).isEqualTo(3);
    }

    @Test
    void aHolidayOnTheStartDateDoesNotChangeTheCount() {
        assertThat(withHolidays(MONDAY).countBusinessDaysAfter(MONDAY, MONDAY.plusDays(3))).isEqualTo(3);
    }

    @Test
    void aHolidayOnTheEndDateDoesNotCountEvenIfItIsTheLastDay() {
        assertThat(withHolidays(MONDAY.plusDays(3)).countBusinessDaysAfter(MONDAY, MONDAY.plusDays(3))).isEqualTo(2);
    }

    @Test
    void thresholdIsReachedOnTheThirdBusinessDayAndNotBefore() {
        BusinessDayCounter counter = withHolidays();

        assertThat(counter.hasReachedBusinessDaysAfter(MONDAY, MONDAY.plusDays(2), 3)).isFalse();
        assertThat(counter.hasReachedBusinessDaysAfter(MONDAY, MONDAY.plusDays(3), 3)).isTrue();
    }

    @Test
    void withAHolidayTheThresholdIsReachedOneBusinessDayLater() {
        BusinessDayCounter counter = withHolidays(TUESDAY);

        assertThat(counter.hasReachedBusinessDaysAfter(MONDAY, MONDAY.plusDays(3), 3)).isFalse();
        assertThat(counter.hasReachedBusinessDaysAfter(MONDAY, MONDAY.plusDays(4), 3)).isTrue();
    }

    @Test
    void rememberingAsksTheCalendarOncePerDay() {
        AtomicInteger asked = new AtomicInteger();
        BusinessDayCounter counter = BusinessDayCounter.remembering(day -> {
            asked.incrementAndGet();
            return true;
        });

        counter.countBusinessDaysAfter(MONDAY, MONDAY.plusDays(3));
        counter.countBusinessDaysAfter(MONDAY, MONDAY.plusDays(3));

        assertThat(asked.get()).isEqualTo(3);
    }

    @Test
    void rejectsMissingDates() {
        BusinessDayCounter counter = withHolidays();

        assertThatThrownBy(() -> counter.countBusinessDaysAfter(null, MONDAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> counter.countBusinessDaysAfter(MONDAY, null)).isInstanceOf(NullPointerException.class);
    }
}
