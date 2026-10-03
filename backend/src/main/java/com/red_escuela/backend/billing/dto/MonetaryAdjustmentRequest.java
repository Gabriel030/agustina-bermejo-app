package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.validation.constraints.NotNull;

public record MonetaryAdjustmentRequest(@NotNull Type type, Money amount, Percentage ratePercent) {

    public enum Type {
        NOMINAL,
        PROPORTIONAL
    }

    public MonetaryAdjustment toAdjustment() {
        return switch (type) {
            case NOMINAL -> {
                if (amount == null || ratePercent != null) {
                    throw new IllegalArgumentException("NOMINAL adjustment requires amount and no ratePercent");
                }
                yield new MonetaryAdjustment.Nominal(amount);
            }
            case PROPORTIONAL -> {
                if (ratePercent == null || amount != null) {
                    throw new IllegalArgumentException("PROPORTIONAL adjustment requires ratePercent and no amount");
                }
                yield new MonetaryAdjustment.Proportional(ratePercent);
            }
        };
    }
}
