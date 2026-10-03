package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate JULY_31 = LocalDate.of(2026, 7, 31);

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> new DateRange(JULY_31, MARCH_1)).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void singleDayRangeIsValid() {
        assertThat(new DateRange(MARCH_1, MARCH_1).contains(MARCH_1)).isTrue();
    }

    @Test
    void nullBoundsAreRejected() {
        assertThatThrownBy(() -> new DateRange(null, JULY_31)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateRange(MARCH_1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void containsIncludesBothBounds() {
        DateRange range = new DateRange(MARCH_1, JULY_31);
        assertThat(range.contains(MARCH_1)).isTrue();
        assertThat(range.contains(JULY_31)).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 5, 15))).isTrue();
        assertThat(range.contains(MARCH_1.minusDays(1))).isFalse();
        assertThat(range.contains(JULY_31.plusDays(1))).isFalse();
    }

    @Test
    void overlapsDetectsSharedDays() {
        DateRange firstHalf = new DateRange(MARCH_1, JULY_31);
        assertThat(firstHalf.overlaps(new DateRange(JULY_31, LocalDate.of(2026, 12, 15)))).isTrue();
        assertThat(firstHalf.overlaps(new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))).isTrue();
        assertThat(firstHalf.overlaps(new DateRange(JULY_31.plusDays(1), LocalDate.of(2026, 12, 15)))).isFalse();
        assertThat(firstHalf.overlaps(new DateRange(LocalDate.of(2026, 1, 1), MARCH_1.minusDays(1)))).isFalse();
    }
}
