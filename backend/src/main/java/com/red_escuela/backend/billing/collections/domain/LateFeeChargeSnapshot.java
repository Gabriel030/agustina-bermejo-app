package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Objects;

public record LateFeeChargeSnapshot(Long chargeId, Long debitNoteId, LateFeeAccrualPeriod period, int chargedDays, NonNegativeMoney baseAmount,
                                    Percentage dailyRate) {

    public LateFeeChargeSnapshot {
        Objects.requireNonNull(chargeId, "chargeId");
        Objects.requireNonNull(debitNoteId, "debitNoteId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(dailyRate, "dailyRate");
        if (chargedDays < 1 || chargedDays > period.days()) {
            throw new IllegalArgumentException("chargedDays must be between 1 and the days of the period");
        }
    }
}
