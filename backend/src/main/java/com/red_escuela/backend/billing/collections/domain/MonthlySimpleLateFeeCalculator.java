package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Objects;

public final class MonthlySimpleLateFeeCalculator implements LateFeeCalculationStrategy {

    @Override
    public NonNegativeMoney calculateLateFee(NonNegativeMoney finalLinePrice, Percentage monthlyRate) {
        Objects.requireNonNull(finalLinePrice, "finalLinePrice");
        Objects.requireNonNull(monthlyRate, "monthlyRate");
        return finalLinePrice.times(monthlyRate);
    }
}
