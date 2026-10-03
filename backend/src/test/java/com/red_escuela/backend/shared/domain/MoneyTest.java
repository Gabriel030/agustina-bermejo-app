package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void amountIsRoundedHalfUpToTwoDecimals() {
        assertThat(ars("100.005").amount()).isEqualTo(new BigDecimal("100.01"));
        assertThat(ars("100.004").amount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void amountAlwaysHasScaleTwo() {
        assertThat(ars("48000").amount()).isEqualTo(new BigDecimal("48000.00"));
        assertThat(ars("48000")).isEqualTo(ars("48000.000"));
    }

    @Test
    void nullAmountOrCurrencyIsRejected() {
        assertThatThrownBy(() -> Money.of(null, Currency.ARS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void zeroIsZeroAndNotNegative() {
        Money zero = Money.zero(Currency.ARS);
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.isNegative()).isFalse();
        assertThat(zero.amount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void plusAndMinusInSameCurrency() {
        assertThat(ars("100.10").plus(ars("0.95"))).isEqualTo(ars("101.05"));
        assertThat(ars("100.00").minus(ars("150.50"))).isEqualTo(ars("-50.50"));
        assertThat(ars("100.00").minus(ars("150.50")).isNegative()).isTrue();
    }

    @Test
    void isGreaterThanComparesAmounts() {
        assertThat(ars("10.01").isGreaterThan(ars("10.00"))).isTrue();
        assertThat(ars("10.00").isGreaterThan(ars("10.00"))).isFalse();
    }

    @Test
    void timesPercentageRoundsTheResultingLine() {
        assertThat(ars("48000").times(Percentage.ofPoints(new BigDecimal("10")))).isEqualTo(ars("4800.00"));
        assertThat(ars("100.05").times(Percentage.ofPoints(new BigDecimal("50")))).isEqualTo(ars("50.03"));
        assertThat(ars("33.33").times(Percentage.ofFraction(new BigDecimal("0.333333")))).isEqualTo(ars("11.11"));
    }

    @Test
    void plusInDifferentCurrenciesFails() {
        Money dollars = Money.of(new BigDecimal("10"), Currency.USD);
        assertThatThrownBy(() -> ars("100").plus(dollars)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void minusInDifferentCurrenciesFails() {
        Money dollars = Money.of(new BigDecimal("10"), Currency.USD);
        assertThatThrownBy(() -> ars("100").minus(dollars)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void comparingDifferentCurrenciesFails() {
        Money dollars = Money.of(new BigDecimal("10"), Currency.USD);
        assertThatThrownBy(() -> ars("100").isGreaterThan(dollars)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void sameAmountInDifferentCurrenciesIsNotEqual() {
        assertThat(ars("10")).isNotEqualTo(Money.of(new BigDecimal("10"), Currency.USD));
    }

    @Test
    void timesKeepsTheCurrency() {
        Money dollars = Money.of(new BigDecimal("200"), Currency.USD);
        assertThat(dollars.times(Percentage.ofPoints(new BigDecimal("10"))))
                .isEqualTo(Money.of(new BigDecimal("20"), Currency.USD));
    }

    @Test
    void timesRejectsNullRate() {
        assertThatThrownBy(() -> ars("1").times(null)).isInstanceOf(NullPointerException.class);
    }
}
