package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record ChangePriceCommand(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom,
                                 String changeReason, Long publisherPersonId) {

    public PriceVersionDraft toDraft(Currency priceCurrency) {
        if (marketingDiscount == null) {
            return PriceVersionDraft.withoutDiscount(priceCurrency, baseAmount.value());
        }
        return marketingDiscount.type() == MarketingDiscountType.PERCENTAGE
                ? PriceVersionDraft.withPercentageDiscount(priceCurrency, baseAmount.value(), marketingDiscount.percentage())
                : PriceVersionDraft.withFixedDiscount(priceCurrency, baseAmount.value(), marketingDiscount.fixedAmount().value());
    }
}
