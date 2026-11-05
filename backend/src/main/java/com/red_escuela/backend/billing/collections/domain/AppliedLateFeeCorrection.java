package com.red_escuela.backend.billing.collections.domain;

import java.util.Objects;

public record AppliedLateFeeCorrection(LateFeeAccrualPeriod correctedRange, int correctedDays) {

    public AppliedLateFeeCorrection {
        Objects.requireNonNull(correctedRange, "correctedRange");
        if (correctedDays < 1 || correctedDays > correctedRange.days()) {
            throw new IllegalArgumentException("correctedDays must be between 1 and the days of the corrected range");
        }
    }
}
