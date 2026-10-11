package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public final class MoneyRatio {

    private static final int FRACTION_SCALE = 6;

    private MoneyRatio() {
    }

    public static Optional<Percentage> of(Money part, Money whole) {
        if (part.currency() != whole.currency()) {
            throw new CurrencyMismatchException(part.currency(), whole.currency());
        }
        if (whole.isZero()) {
            return Optional.empty();
        }
        BigDecimal fraction = part.amount().divide(whole.amount(), FRACTION_SCALE, RoundingMode.HALF_UP);
        return Optional.of(Percentage.ofFraction(fraction.min(BigDecimal.ONE)));
    }
}
