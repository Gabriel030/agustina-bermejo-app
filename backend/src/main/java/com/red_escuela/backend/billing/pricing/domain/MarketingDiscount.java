package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketingDiscount(MarketingDiscountType type, BigDecimal value) {

    public MarketingDiscount {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    public static MarketingDiscount ofPercentage(Percentage percentage) {
        return new MarketingDiscount(MarketingDiscountType.PERCENTAGE, percentage.points());
    }

    public static MarketingDiscount ofFixedAmount(NonNegativeMoney amount) {
        return new MarketingDiscount(MarketingDiscountType.FIXED, amount.value().amount());
    }

    public NonNegativeMoney discountOn(NonNegativeMoney base) {
        Objects.requireNonNull(base, "base");
        return switch (type) {
            case PERCENTAGE -> base.times(Percentage.ofPoints(value));
            case FIXED -> NonNegativeMoney.of(value, base.value().currency());
        };
    }
}
