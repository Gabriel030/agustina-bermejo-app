package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.util.Objects;

public final class DailySimpleLateFeeCalculator implements LateFeeCalculationStrategy {

    @Override
    public NonNegativeMoney calculateLateFee(NonNegativeMoney finalLinePrice, Percentage dailyRate, int chargedDays) {
        Objects.requireNonNull(finalLinePrice, "finalLinePrice");
        Objects.requireNonNull(dailyRate, "dailyRate");
        if (chargedDays < 0) {
            throw new IllegalArgumentException("chargedDays must not be negative");
        }
        return finalLinePrice.times(Percentage.ofFraction(dailyRate.fraction().multiply(BigDecimal.valueOf(chargedDays))));
    }
}
