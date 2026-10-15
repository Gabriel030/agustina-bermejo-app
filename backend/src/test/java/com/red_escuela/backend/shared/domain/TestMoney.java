package com.red_escuela.backend.shared.domain;

import java.math.BigDecimal;

public final class TestMoney {

    private TestMoney() {
    }

    public static Money ars(BigDecimal amount) {
        return Money.of(amount, Currency.ARS);
    }

    public static Money ars(long amount) {
        return Money.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    public static Money ars(double amount) {
        return Money.of(BigDecimal.valueOf(amount), Currency.ARS);
    }

    public static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    public static Money usd(long amount) {
        return Money.of(BigDecimal.valueOf(amount), Currency.USD);
    }

    public static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), Currency.USD);
    }
}
