package com.red_escuela.backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LegalAgeTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Test
    void personTurningEighteenTodayIsAdult() {
        assertThat(LegalAge.isMinor(LocalDate.of(2008, 11, 7), TODAY)).isFalse();
    }

    @Test
    void personTurningEighteenTomorrowIsMinor() {
        assertThat(LegalAge.isMinor(LocalDate.of(2008, 11, 8), TODAY)).isTrue();
    }

    @Test
    void personBornOnLeapDayIsAdultOnFebruaryTwentyEighthOfTheEighteenthYear() {
        assertThat(LegalAge.isMinor(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 27))).isTrue();
        assertThat(LegalAge.isMinor(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28))).isFalse();
    }
}
