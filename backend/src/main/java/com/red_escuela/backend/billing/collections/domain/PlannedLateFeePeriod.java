package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;

import java.util.Objects;

public record PlannedLateFeePeriod(LateFeeAccrualPeriod period, int chargedDays, LateFeeCutoffKind kind) {

    public PlannedLateFeePeriod {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(kind, "kind");
        if (chargedDays < 0 || chargedDays > period.days()) {
            throw new IllegalArgumentException("chargedDays must be between 0 and the days of the period");
        }
    }
}
