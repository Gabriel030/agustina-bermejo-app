package com.red_escuela.backend.billing.reporting.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record MoneyValue(@JsonValue Money money) implements MetricValue {

    public MoneyValue {
        Objects.requireNonNull(money, "money");
    }

    @Override
    public MetricUnit unit() {
        return MetricUnit.MONEY;
    }

    @Override
    public BigDecimal magnitude() {
        return money.amount();
    }
}
