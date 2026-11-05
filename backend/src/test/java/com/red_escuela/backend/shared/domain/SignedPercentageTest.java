package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedPercentageTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void anIncreaseOverTheBaseIsPositive() {
        assertThat(SignedPercentage.changeBetween(ars("900000"), ars("1000000"))).contains(SignedPercentage.ofFraction(new BigDecimal("0.111111")));
        assertThat(SignedPercentage.changeBetween(ars("900000"), ars("1000000")).orElseThrow().points()).isEqualTo(new BigDecimal("11.11"));
    }

    @Test
    void aDecreaseIsNegative() {
        assertThat(SignedPercentage.changeBetween(ars("1000"), ars("750")).orElseThrow().points()).isEqualTo(new BigDecimal("-25.00"));
    }

    @Test
    void aChangeOverOneHundredPercentIsRepresentable() {
        assertThat(SignedPercentage.changeBetween(ars("100"), ars("350")).orElseThrow().points()).isEqualTo(new BigDecimal("250.00"));
    }

    @Test
    void noChangeIsZero() {
        assertThat(SignedPercentage.changeBetween(ars("100"), ars("100")).orElseThrow().fraction()).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    void aZeroBaseHasNoPercentage() {
        assertThat(SignedPercentage.changeBetween(ars("0"), ars("100"))).isEmpty();
    }

    @Test
    void pointsAndFractionAreEquivalentWithSign() {
        assertThat(SignedPercentage.ofPoints(new BigDecimal("-12.5"))).isEqualTo(SignedPercentage.ofFraction(new BigDecimal("-0.125")));
    }

    @Test
    void differentCurrenciesAreRejected() {
        assertThatThrownBy(() -> SignedPercentage.changeBetween(ars("100"), Money.of(BigDecimal.TEN, Currency.USD))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> SignedPercentage.ofFraction(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SignedPercentage.ofPoints(null)).isInstanceOf(NullPointerException.class);
    }
}
