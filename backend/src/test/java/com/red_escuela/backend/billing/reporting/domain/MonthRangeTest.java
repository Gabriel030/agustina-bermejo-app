package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.billing.reporting.exception.InvalidMonthRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthRangeTest {

    @Test
    void aRangeOfExactlyThirtySixMonthsIsAccepted() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2029, 2));

        assertThat(range.months()).hasSize(36).startsWith(YearMonth.of(2026, 3)).endsWith(YearMonth.of(2029, 2));
    }

    @Test
    void aRangeOfThirtySevenMonthsIsRejected() {
        assertThatThrownBy(() -> new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2029, 3))).isInstanceOf(InvalidMonthRangeException.class);
    }

    @Test
    void aRangeThatSpansFortySixMonthsIsRejected() {
        assertThatThrownBy(() -> new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2029, 12))).isInstanceOf(InvalidMonthRangeException.class);
    }

    @Test
    void aRangeWhoseStartIsAfterItsEndIsRejected() {
        assertThatThrownBy(() -> new MonthRange(YearMonth.of(2026, 4), YearMonth.of(2026, 3))).isInstanceOf(InvalidMonthRangeException.class);
    }

    @Test
    void aSingleMonthRangeIsAccepted() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 3), YearMonth.of(2026, 3));

        assertThat(range.months()).containsExactly(YearMonth.of(2026, 3));
        assertThat(range.indexOf(YearMonth.of(2026, 3))).isZero();
    }

    @Test
    void theLastMonthsEndingAtCoverTheRequestedAmountOfMonths() {
        MonthRange range = MonthRange.lastMonthsEndingAt(YearMonth.of(2026, 10), 12);

        assertThat(range.from()).isEqualTo(YearMonth.of(2025, 11));
        assertThat(range.to()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(range.months()).hasSize(12);
    }

    @Test
    void indexesAndMembershipFollowTheMonthOrder() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 11), YearMonth.of(2027, 2));

        assertThat(range.indexOf(YearMonth.of(2027, 1))).isEqualTo(2);
        assertThat(range.contains(YearMonth.of(2026, 11))).isTrue();
        assertThat(range.contains(YearMonth.of(2027, 2))).isTrue();
        assertThat(range.contains(YearMonth.of(2026, 10))).isFalse();
        assertThat(range.contains(YearMonth.of(2027, 3))).isFalse();
    }

    @Test
    void theBoundaryDaysCoverWholeMonths() {
        MonthRange range = new MonthRange(YearMonth.of(2026, 12), YearMonth.of(2027, 1));

        assertThat(range.firstDay()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(range.dayAfterLastDay()).isEqualTo(LocalDate.of(2027, 2, 1));
    }
}
