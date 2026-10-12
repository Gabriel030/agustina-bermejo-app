package com.red_escuela.backend.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public record NonNegativeMoney(@Embedded @JsonValue Money value) {

    public NonNegativeMoney {
        Objects.requireNonNull(value, "value");
        if (value.isNegative()) {
            throw new NegativeMoneyAmountException();
        }
    }

    public static NonNegativeMoney of(BigDecimal amount, Currency currency) {
        return new NonNegativeMoney(Money.of(amount, currency));
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static NonNegativeMoney of(Money money) {
        return new NonNegativeMoney(money);
    }

    public static NonNegativeMoney zero(Currency currency) {
        return new NonNegativeMoney(Money.zero(currency));
    }

    public NonNegativeMoney plus(NonNegativeMoney other) {
        Objects.requireNonNull(other, "other");
        return new NonNegativeMoney(value.plus(other.value));
    }

    public NonNegativeMoney minusNotBelowZero(NonNegativeMoney other) {
        Objects.requireNonNull(other, "other");
        Money difference = value.minus(other.value);
        return difference.isNegative() ? zero(value.currency()) : new NonNegativeMoney(difference);
    }

    public Money minus(NonNegativeMoney other) {
        Objects.requireNonNull(other, "other");
        return value.minus(other.value);
    }

    public NonNegativeMoney times(Percentage rate) {
        return new NonNegativeMoney(value.times(rate));
    }
}
