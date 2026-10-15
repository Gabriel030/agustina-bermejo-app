package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateTimeRangeTest {

    private static final LocalDateTime OPENS = LocalDateTime.of(2026, 2, 1, 9, 0);
    private static final LocalDateTime CLOSES = LocalDateTime.of(2026, 2, 15, 23, 59);

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> new DateTimeRange(CLOSES, OPENS)).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void nullBoundsAreRejected() {
        assertThatThrownBy(() -> new DateTimeRange(null, CLOSES)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateTimeRange(OPENS, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void containsIncludesBothBoundsKeepingTheTime() {
        DateTimeRange window = new DateTimeRange(OPENS, CLOSES);
        assertThat(window.contains(OPENS)).isTrue();
        assertThat(window.contains(CLOSES)).isTrue();
        assertThat(window.contains(OPENS.minusMinutes(1))).isFalse();
        assertThat(window.contains(CLOSES.plusMinutes(1))).isFalse();
    }
}
