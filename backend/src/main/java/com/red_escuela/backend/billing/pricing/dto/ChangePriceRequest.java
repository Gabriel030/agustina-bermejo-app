package com.red_escuela.backend.billing.pricing.dto;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ChangePriceRequest(@NotNull NonNegativeMoney amount, @Valid MarketingDiscountRequest marketingDiscount, @NotNull LocalDate effectiveFrom, String changeReason,
                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Min(2000) @Max(2200) Integer appliesToLectiveYear) {

    public MarketingDiscount discountOrNull() {
        return marketingDiscount == null ? null : marketingDiscount.toDiscount();
    }
}
