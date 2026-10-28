package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.notification.reporting.exception.InvalidMonthRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthWindowTest {

    @Test
    void aWindowOfThirtySixMonthsIsAllowed() {
        MonthWindow window = new MonthWindow(YearMonth.of(2024, 1), YearMonth.of(2026, 12));

        assertThat(window.months()).hasSize(36);
    }

    @Test
    void aWindowOfThirtySevenMonthsIsRejected() {
        assertThatThrownBy(() -> new MonthWindow(YearMonth.of(2024, 1), YearMonth.of(2027, 1))).isInstanceOf(InvalidMonthRangeException.class);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> new MonthWindow(YearMonth.of(2026, 4), YearMonth.of(2026, 3))).isInstanceOf(InvalidMonthRangeException.class);
    }

    @Test
    void aSingleMonthWindowIsAllowed() {
        assertThat(new MonthWindow(YearMonth.of(2026, 3), YearMonth.of(2026, 3)).months()).containsExactly(YearMonth.of(2026, 3));
    }

    @Test
    void theWindowCoversFromTheFirstDayOfTheFirstMonthUntilTheDayAfterTheLastOne() {
        MonthWindow window = new MonthWindow(YearMonth.of(2026, 3), YearMonth.of(2026, 12));

        assertThat(window.firstDay()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(window.dayAfterLastDay()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void containsAndIndexOfFollowTheMonthsOfTheWindow() {
        MonthWindow window = new MonthWindow(YearMonth.of(2026, 3), YearMonth.of(2026, 5));

        assertThat(window.contains(YearMonth.of(2026, 2))).isFalse();
        assertThat(window.contains(YearMonth.of(2026, 3))).isTrue();
        assertThat(window.contains(YearMonth.of(2026, 5))).isTrue();
        assertThat(window.contains(YearMonth.of(2026, 6))).isFalse();
        assertThat(window.indexOf(YearMonth.of(2026, 5))).isEqualTo(2);
    }
}
