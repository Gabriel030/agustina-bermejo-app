package com.red_escuela.backend.billing.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MissedNightsTest {

    private static final YearMonth APRIL = YearMonth.of(2023, 4);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);
    private static final LocalDate LONG_AFTER = LocalDate.of(2024, 1, 1);

    @Test
    void aFullMonthWithoutRecordsMissesEveryDay() {
        assertThat(MissedNights.countIn(APRIL, LONG_AGO, LONG_AFTER, Set.of())).isEqualTo(30);
    }

    @Test
    void everyRecordedNightIsSubtractedFromTheMissedOnes() {
        assertThat(MissedNights.countIn(APRIL, LONG_AGO, LONG_AFTER, Set.of(LocalDate.of(2023, 4, 1), LocalDate.of(2023, 4, 30), LocalDate.of(2023, 4, 15)))).isEqualTo(27);
    }

    @Test
    void theDayOfTheCallAndTheFutureAreNotMissedYet() {
        assertThat(MissedNights.countIn(APRIL, LONG_AGO, LocalDate.of(2023, 4, 10), Set.of())).isEqualTo(9);
        assertThat(MissedNights.countIn(APRIL, LONG_AGO, LocalDate.of(2023, 4, 1), Set.of())).isZero();
        assertThat(MissedNights.countIn(APRIL, LONG_AGO, LocalDate.of(2023, 4, 2), Set.of())).isEqualTo(1);
    }

    @Test
    void daysBeforeTheFirstRecordedNightAreNotMissed() {
        assertThat(MissedNights.countIn(APRIL, LocalDate.of(2023, 4, 21), LONG_AFTER, Set.of())).isEqualTo(10);
        assertThat(MissedNights.countIn(APRIL, LocalDate.of(2023, 4, 30), LONG_AFTER, Set.of())).isEqualTo(1);
        assertThat(MissedNights.countIn(APRIL, LocalDate.of(2023, 5, 1), LONG_AFTER, Set.of())).isZero();
    }

    @Test
    void aMonthLongBeforeTheFirstRecordedNightOrAfterTodayHasNoMissedNights() {
        assertThat(MissedNights.countIn(YearMonth.of(2019, 12), LONG_AGO, LONG_AFTER, Set.of())).isZero();
        assertThat(MissedNights.countIn(YearMonth.of(2024, 6), LONG_AGO, LONG_AFTER, Set.of())).isZero();
    }
}
