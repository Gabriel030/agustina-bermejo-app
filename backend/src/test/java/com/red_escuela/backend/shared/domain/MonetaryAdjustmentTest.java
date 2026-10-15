package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonetaryAdjustmentTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void nominalAdjustmentIgnoresTheBase() {
        MonetaryAdjustment nominal = new MonetaryAdjustment.Nominal(ars("5000"));
        assertThat(nominal.applyTo(ars("48000"))).isEqualTo(ars("5000.00"));
        assertThat(nominal.applyTo(ars("999999"))).isEqualTo(ars("5000.00"));
    }

    @Test
    void proportionalAdjustmentAppliesTheRateToTheBase() {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));
        assertThat(proportional.applyTo(ars("48000"))).isEqualTo(ars("4800.00"));
    }

    @Test
    void nominalInADifferentCurrencyThanTheBaseFails() {
        MonetaryAdjustment nominal = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("50"), Currency.USD));
        assertThatThrownBy(() -> nominal.applyTo(ars("48000"))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void proportionalKeepsTheBaseCurrency() {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));
        assertThat(proportional.applyTo(Money.of(new BigDecimal("300"), Currency.USD)))
                .isEqualTo(Money.of(new BigDecimal("30"), Currency.USD));
    }

    @Test
    void negativeNominalAmountIsRejected() {
        assertThatThrownBy(() -> new MonetaryAdjustment.Nominal(ars("-100")))
                .isInstanceOf(NegativeAdjustmentAmountException.class);
    }

    @Test
    void zeroNominalAmountIsAllowed() {
        assertThat(new MonetaryAdjustment.Nominal(Money.zero(Currency.ARS)).applyTo(ars("100")).isZero()).isTrue();
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> new MonetaryAdjustment.Nominal(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MonetaryAdjustment.Proportional(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void proportionalResolvesAgainstDifferentBases() {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.3")));
        assertThat(proportional.applyTo(ars("1000"))).isEqualTo(ars("300.00"));
    }

    @Test
    void proportionalAlwaysResolvesWithScaleTwoAndHalfUpRounding() {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofFraction(new BigDecimal("0.335")));
        Money result = proportional.applyTo(ars("100.005"));
        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result).isEqualTo(ars("33.50"));
    }

    @Test
    void exhaustiveSwitchCoversBothVariants() {
        MonetaryAdjustment adjustment = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("15")));
        String description = switch (adjustment) {
            case MonetaryAdjustment.Nominal nominal -> "nominal " + nominal.amount().amount();
            case MonetaryAdjustment.Proportional proportional -> "proportional " + proportional.rate().points();
        };
        assertThat(description).isEqualTo("proportional 15.00");
    }
}
