package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

public enum MetricUnit {
    COUNT,
    MONEY,
    PERCENT,
    DAYS;

    public MetricValue valueWhenMonthHasNoActivity(Currency currency) {
        return switch (this) {
            case COUNT -> new CountValue(0);
            case MONEY -> new MoneyValue(Money.zero(currency));
            case PERCENT, DAYS -> null;
        };
    }
}
