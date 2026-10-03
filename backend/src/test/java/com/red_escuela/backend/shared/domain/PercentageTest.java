package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageTest {

    @Test
    void twentyFivePointsEqualsAQuarterFraction() {
        assertThat(Percentage.ofPoints(new BigDecimal("25"))).isEqualTo(Percentage.ofFraction(new BigDecimal("0.25")));
    }

    @Test
    void zeroAndOneHundredPointsAreValidBounds() {
        assertThat(Percentage.ofPoints(BigDecimal.ZERO).fraction()).isEqualTo(new BigDecimal("0.000000"));
        assertThat(Percentage.ofPoints(new BigDecimal("100")).fraction()).isEqualTo(new BigDecimal("1.000000"));
        assertThat(Percentage.ofFraction(BigDecimal.ONE).points()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void pointsOutsideZeroToOneHundredAreRejected() {
        assertThatThrownBy(() -> Percentage.ofPoints(new BigDecimal("-0.01")))
                .isInstanceOf(InvalidPercentageException.class);
        assertThatThrownBy(() -> Percentage.ofPoints(new BigDecimal("100.01")))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void fractionOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> Percentage.ofFraction(new BigDecimal("1.5")))
                .isInstanceOf(InvalidPercentageException.class);
        assertThatThrownBy(() -> Percentage.ofFraction(new BigDecimal("-0.1")))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> Percentage.ofFraction(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Percentage.ofPoints(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void ratioOfOneThirdIsRoundedToSixDecimals() {
        assertThat(Percentage.ratio(1, 3)).contains(Percentage.ofFraction(new BigDecimal("0.333333")));
        assertThat(Percentage.ratio(2, 3)).contains(Percentage.ofFraction(new BigDecimal("0.666667")));
    }

    @Test
    void ratioWithZeroTotalIsEmpty() {
        assertThat(Percentage.ratio(0, 0)).isEmpty();
        assertThat(Percentage.ratio(5, 0)).isEmpty();
    }

    @Test
    void ratioAboveTheTotalIsRejected() {
        assertThatThrownBy(() -> Percentage.ratio(4, 3)).isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void pointsHaveTwoDecimals() {
        assertThat(Percentage.ofFraction(new BigDecimal("0.875")).points()).isEqualTo(new BigDecimal("87.50"));
        assertThat(Percentage.ratio(1, 3).orElseThrow().points()).isEqualTo(new BigDecimal("33.33"));
        assertThat(Percentage.ratio(2, 3).orElseThrow().points()).isEqualTo(new BigDecimal("66.67"));
    }

    @Test
    void exceedsComparesFractions() {
        Percentage quarter = Percentage.ofPoints(new BigDecimal("25"));
        Percentage third = Percentage.ratio(1, 3).orElseThrow();
        assertThat(third.exceeds(quarter)).isTrue();
        assertThat(quarter.exceeds(third)).isFalse();
        assertThat(quarter.exceeds(quarter)).isFalse();
    }
}
