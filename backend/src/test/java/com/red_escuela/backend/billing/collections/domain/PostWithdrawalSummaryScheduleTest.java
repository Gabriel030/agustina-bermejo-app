package com.red_escuela.backend.billing.collections.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PostWithdrawalSummaryScheduleTest {
    private static final LocalDate WITHDRAWAL = LocalDate.of(2027, 7, 31);
    private static final NonBusinessDays WEEKENDS_ONLY = NonBusinessDays.weekendsOnly();

    @Test
    @Tag("D-338")
    void theSummaryGoesOutOnTheFirstOfEachOfTheSixMonthsAfterTheWithdrawalAndNotOnTheSeventh() {
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 9, 1), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 10, 1), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 12, 1), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2028, 1, 3), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2028, 2, 1), 6, WEEKENDS_ONLY)).isFalse();
    }

    @Test
    @Tag("D-338")
    void theFirstSummaryIsOfTheMonthAfterTheWithdrawalAndNeverOfTheMonthOfTheWithdrawal() {
        LocalDate withdrawalOnTheFirst = LocalDate.of(2027, 9, 1);

        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(withdrawalOnTheFirst, LocalDate.of(2027, 9, 1), 6, WEEKENDS_ONLY)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(withdrawalOnTheFirst, LocalDate.of(2027, 10, 1), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(LocalDate.of(2027, 7, 15), LocalDate.of(2027, 8, 2), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(LocalDate.of(2027, 7, 15), LocalDate.of(2027, 7, 1), 6, WEEKENDS_ONLY)).isFalse();
    }

    @Test
    @Tag("D-338")
    void aFirstOfMonthThatIsNotABusinessDayMovesToTheNextBusinessDayAndNotToThePreviousOne() {
        assertThat(PostWithdrawalSummarySchedule.summaryDayOfMonth(YearMonth.of(2027, 8), WEEKENDS_ONLY)).isEqualTo(LocalDate.of(2027, 8, 2));
        assertThat(PostWithdrawalSummarySchedule.summaryDayOfMonth(YearMonth.of(2027, 9), WEEKENDS_ONLY)).isEqualTo(LocalDate.of(2027, 9, 1));
        NonBusinessDays holidayOnFirstAndSecond = new NonBusinessDays(Set.of(LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 2)));
        assertThat(PostWithdrawalSummarySchedule.summaryDayOfMonth(YearMonth.of(2027, 9), holidayOnFirstAndSecond)).isEqualTo(LocalDate.of(2027, 9, 3));
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 1), 6, WEEKENDS_ONLY)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 2), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 3), 6, WEEKENDS_ONLY)).isTrue();
    }

    @Test
    @Tag("D-338")
    void theNumberOfMonthsComesFromTheConfigurationOfTheDayAndOneMonthStopsAfterTheFirstSummary() {
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 2), 1, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 9, 1), 1, WEEKENDS_ONLY)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2029, 7, 2), 24, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2029, 8, 1), 24, WEEKENDS_ONLY)).isFalse();
    }

    @Test
    @Tag("D-338")
    void aBusinessDayAfterTheSummaryDayOfTheSameMonthStillOwesTheSummaryButAWeekendOrAHolidayDoesNot() {
        NonBusinessDays holidayOnThe4th = new NonBusinessDays(Set.of(LocalDate.of(2027, 8, 4)));

        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 31), 6, WEEKENDS_ONLY)).isTrue();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 7), 6, WEEKENDS_ONLY)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 4), 6, holidayOnThe4th)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 8, 5), 6, holidayOnThe4th)).isTrue();
    }

    @Test
    @Tag("D-338")
    void theRecoveryNeverReachesAMonthOutsideTheConfiguredWindow() {
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2028, 2, 3), 6, WEEKENDS_ONLY)).isFalse();
        assertThat(PostWithdrawalSummarySchedule.isSummaryDueOn(WITHDRAWAL, LocalDate.of(2027, 7, 30), 6, WEEKENDS_ONLY)).isFalse();
    }
}
