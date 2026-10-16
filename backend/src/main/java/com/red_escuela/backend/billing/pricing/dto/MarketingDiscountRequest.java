package com.red_escuela.backend.billing.pricing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;


public record MarketingDiscountRequest(@NotNull MarketingDiscountType type, Percentage percent, NonNegativeMoney fixedAmount) {

    @JsonIgnore
    @AssertTrue(message = "El descuento de marketing lleva solo el valor de su tipo.")
    public boolean isConsistentWithItsType() {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case PERCENTAGE -> percent != null && fixedAmount == null;
            case FIXED -> fixedAmount != null && percent == null;
        };
    }

    public MarketingDiscount toDiscount() {
        return type == MarketingDiscountType.PERCENTAGE
                ? MarketingDiscount.ofPercentage(percent)
                : MarketingDiscount.ofFixedAmount(fixedAmount);
    }
}
