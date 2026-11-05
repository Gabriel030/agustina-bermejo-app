package com.red_escuela.backend.billing.pricing.dto;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ChangePriceRequest(@NotNull NonNegativeMoney amount, @Valid MarketingDiscountRequest marketingDiscount, @NotNull LocalDate effectiveFrom, String changeReason) {

    public MarketingDiscount discountOrNull() {
        return marketingDiscount == null ? null : marketingDiscount.toDiscount();
    }
}
