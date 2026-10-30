package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;

public final class AtypicalInstallmentIncreaseRule {
    public static final Percentage THRESHOLD = Percentage.ofPoints(new BigDecimal("15"));

    private AtypicalInstallmentIncreaseRule() {
    }

    public static boolean isAtypicalIncrease(Money previousTotal, Money currentTotal) {
        return !previousTotal.isZero() && currentTotal.isGreaterThan(previousTotal.plus(previousTotal.times(THRESHOLD)));
    }
}
