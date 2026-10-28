package com.red_escuela.backend.billing.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthEndSnapshotChoiceTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    @Test
    void theSnapshotOfTheLastDayOfTheMonthIsChosenAndIsNotPartial() {
        MonthEndSnapshotChoice choice = MonthEndSnapshotChoice.choose(MARCH, List.of(LocalDate.of(2026, 3, 30), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1))).orElseThrow();

        assertThat(choice.snapshotDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(choice.partial()).isFalse();
    }

    @Test
    void withoutTheLastDayTheLatestPreviousSnapshotOfTheSameMonthIsChosenAndFlaggedPartial() {
        MonthEndSnapshotChoice choice = MonthEndSnapshotChoice.choose(MARCH, List.of(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30), LocalDate.of(2026, 2, 28))).orElseThrow();

        assertThat(choice.snapshotDate()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(choice.partial()).isTrue();
    }

    @Test
    void snapshotsOfOtherMonthsNeverFillAMonthWithoutData() {
        assertThat(MonthEndSnapshotChoice.choose(MARCH, List.of(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 4, 1)))).isEmpty();
        assertThat(MonthEndSnapshotChoice.choose(MARCH, List.of())).isEmpty();
    }

    @Test
    void theLastDayOfShortAndLeapMonthsIsTheirOwn() {
        assertThat(MonthEndSnapshotChoice.choose(YearMonth.of(2024, 2), List.of(LocalDate.of(2024, 2, 29))).orElseThrow().partial()).isFalse();
        assertThat(MonthEndSnapshotChoice.choose(YearMonth.of(2025, 2), List.of(LocalDate.of(2025, 2, 28))).orElseThrow().partial()).isFalse();
        assertThat(MonthEndSnapshotChoice.choose(YearMonth.of(2024, 2), List.of(LocalDate.of(2024, 2, 28))).orElseThrow().partial()).isTrue();
    }

    @Test
    void aChoiceNeedsADate() {
        assertThatThrownBy(() -> new MonthEndSnapshotChoice(null, false)).isInstanceOf(NullPointerException.class);
    }
}
