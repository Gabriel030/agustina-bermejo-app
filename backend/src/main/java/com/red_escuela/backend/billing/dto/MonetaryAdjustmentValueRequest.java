package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;

import java.math.BigDecimal;

public record MonetaryAdjustmentValueRequest(BigDecimal fixedAmount, BigDecimal percentage) {
    public MonetaryAdjustmentValue toValue() {
        if (fixedAmount != null && percentage != null) {
            throw new IllegalArgumentException("Only one of fixedAmount/percentage can be set, not both");
        }
        if (fixedAmount != null) {
            return new MonetaryAdjustmentValue.FixedAmount(fixedAmount);
        }
        if (percentage != null) {
            return new MonetaryAdjustmentValue.PercentageAmount(percentage);
        }
        throw new IllegalArgumentException("Either fixedAmount or percentage must be set");
    }
}
