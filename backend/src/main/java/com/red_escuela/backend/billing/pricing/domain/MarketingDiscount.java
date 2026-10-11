package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Objects;

public record MarketingDiscount(MarketingDiscountType type, Percentage percentage, NonNegativeMoney fixedAmount) {

    public MarketingDiscount {
        Objects.requireNonNull(type, "type");
        boolean consistent = switch (type) {
            case PERCENTAGE -> percentage != null && fixedAmount == null;
            case FIXED -> fixedAmount != null && percentage == null;
        };
        if (!consistent) {
            throw new IllegalArgumentException("A " + type + " marketing discount carries exactly its own value");
        }
    }

    public static MarketingDiscount ofPercentage(Percentage percentage) {
        return new MarketingDiscount(MarketingDiscountType.PERCENTAGE, Objects.requireNonNull(percentage, "percentage"), null);
    }

    public static MarketingDiscount ofFixedAmount(NonNegativeMoney amount) {
        return new MarketingDiscount(MarketingDiscountType.FIXED, null, Objects.requireNonNull(amount, "amount"));
    }

    public NonNegativeMoney discountOn(NonNegativeMoney base) {
        Objects.requireNonNull(base, "base");
        return switch (type) {
            case PERCENTAGE -> base.times(percentage);
            case FIXED -> {
                if (fixedAmount.value().currency() != base.value().currency()) {
                    throw new IllegalArgumentException("A " + fixedAmount.value().currency() + " discount cannot apply to a " + base.value().currency() + " price");
                }
                yield fixedAmount;
            }
        };
    }
}
