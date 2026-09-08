package com.red_escuela.backend.billing.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Polymorphic Value Object (sealed interface): "fixed amount or percentage, never both" is an
// invariant of the type itself, not a DB constraint that a direct UPDATE could bypass.
public sealed interface MonetaryAdjustmentValue permits MonetaryAdjustmentValue.FixedAmount, MonetaryAdjustmentValue.PercentageAmount {

    BigDecimal resolve(BigDecimal baseAmount);

    record FixedAmount(BigDecimal amount) implements MonetaryAdjustmentValue {
        public FixedAmount {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }

        @Override
        public BigDecimal resolve(BigDecimal baseAmount) {
            return amount;
        }
    }

    record PercentageAmount(BigDecimal percentage) implements MonetaryAdjustmentValue {
        public PercentageAmount {
            if (percentage == null || percentage.signum() < 0 || percentage.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("percentage must be between 0 and 1");
            }
        }

        @Override
        public BigDecimal resolve(BigDecimal baseAmount) {
            return baseAmount.multiply(percentage).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
