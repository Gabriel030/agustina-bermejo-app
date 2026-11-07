package com.red_escuela.backend.billing.run.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-452")
class DraftReviewAlertScheduleTest {
    private static final LocalDate PERIOD = LocalDate.of(2043, 3, 1);
    private static final Predicate<LocalDate> WEEKDAYS_ONLY = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;

    @Test
    void nothingIsAlertedBeforeTheFifthDay() {
        for (int day = 1; day <= 4; day++) {
            assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, PERIOD.withDayOfMonth(day), date -> true)).isFalse();
        }
    }

    @Test
    void theFifthDayAlertsEvenWhenItIsNotABusinessDay() {
        LocalDate fifth = PERIOD.withDayOfMonth(5);
        assertThat(fifth.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);

        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, fifth, date -> false)).isTrue();
    }

    @Test
    void afterTheFifthDayOnlyBusinessDaysAlert() {
        LocalDate friday = PERIOD.withDayOfMonth(6);
        LocalDate saturday = PERIOD.withDayOfMonth(7);
        LocalDate monday = PERIOD.withDayOfMonth(9);

        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, friday, WEEKDAYS_ONLY)).isTrue();
        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, saturday, WEEKDAYS_ONLY)).isFalse();
        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, monday, WEEKDAYS_ONLY)).isTrue();
    }

    @Test
    void aHolidayAfterTheFifthDayDoesNotAlert() {
        LocalDate holiday = PERIOD.withDayOfMonth(10);
        Set<LocalDate> holidays = Set.of(holiday);

        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, holiday, date -> WEEKDAYS_ONLY.test(date) && !holidays.contains(date))).isFalse();
    }

    @Test
    void aRunOfAnEarlierMonthKeepsAlertingInTheFollowingMonth() {
        assertThat(DraftReviewAlertSchedule.alertsOn(PERIOD, LocalDate.of(2043, 4, 1), WEEKDAYS_ONLY)).isTrue();
    }
}
