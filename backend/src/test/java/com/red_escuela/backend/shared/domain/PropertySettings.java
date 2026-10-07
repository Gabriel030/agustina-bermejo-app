package com.red_escuela.backend.shared.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

import java.math.BigDecimal;

final class PropertySettings {

    static final int TRIES = 1000;
    static final String SEED = "20261006";

    private PropertySettings() {
    }

    static Arbitrary<BigDecimal> anyAmount() {
        return Arbitraries.bigDecimals().between(new BigDecimal("-1000000.00"), new BigDecimal("1000000.00")).ofScale(2);
    }

    static Arbitrary<BigDecimal> nonNegativeAmount() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("1000000.00")).ofScale(2);
    }

    static Arbitrary<BigDecimal> negativeAmount() {
        return Arbitraries.bigDecimals().between(new BigDecimal("-1000000.00"), new BigDecimal("-0.01")).ofScale(2);
    }

    static Arbitrary<Currency> anyCurrency() {
        return Arbitraries.of(Currency.class);
    }

    static Arbitrary<Money> anyMoneyIn(Currency currency) {
        return anyAmount().map(amount -> Money.of(amount, currency));
    }

    static Arbitrary<Percentage> anyPercentage() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("100.00")).ofScale(2).map(Percentage::ofPoints);
    }
}
