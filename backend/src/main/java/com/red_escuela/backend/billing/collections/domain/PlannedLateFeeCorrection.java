package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record PlannedLateFeeCorrection(LateFeeAccrualPeriod correctedRange, int correctedDays, NonNegativeMoney creditAmount) {

    public PlannedLateFeeCorrection {
        Objects.requireNonNull(correctedRange, "correctedRange");
        Objects.requireNonNull(creditAmount, "creditAmount");
        if (correctedDays < 1 || correctedDays > correctedRange.days()) {
            throw new IllegalArgumentException("correctedDays must be between 1 and the days of the corrected range");
        }
    }
}
