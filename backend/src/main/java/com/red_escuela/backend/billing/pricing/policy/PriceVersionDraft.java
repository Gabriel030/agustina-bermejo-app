package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.util.Objects;

public record PriceVersionDraft(Currency priceCurrency, Money baseAmount, MarketingDiscountType discountType, Percentage discountPercentage, Money discountAmount) {

    public PriceVersionDraft {
        Objects.requireNonNull(priceCurrency, "priceCurrency");
        Objects.requireNonNull(baseAmount, "baseAmount");
        boolean consistent = discountType == null
                ? discountPercentage == null && discountAmount == null
                : switch (discountType) {
                    case PERCENTAGE -> discountPercentage != null && discountAmount == null;
                    case FIXED -> discountAmount != null && discountPercentage == null;
                };
        if (!consistent) {
            throw new IllegalArgumentException("A marketing discount needs its type and exactly its own value");
        }
    }

    public static PriceVersionDraft withoutDiscount(Currency priceCurrency, Money baseAmount) {
        return new PriceVersionDraft(priceCurrency, baseAmount, null, null, null);
    }

    public static PriceVersionDraft withPercentageDiscount(Currency priceCurrency, Money baseAmount, Percentage percentage) {
        return new PriceVersionDraft(priceCurrency, baseAmount, MarketingDiscountType.PERCENTAGE, percentage, null);
    }

    public static PriceVersionDraft withFixedDiscount(Currency priceCurrency, Money baseAmount, Money discountAmount) {
        return new PriceVersionDraft(priceCurrency, baseAmount, MarketingDiscountType.FIXED, null, discountAmount);
    }

    public boolean hasDiscountOfType(MarketingDiscountType type) {
        return discountType == type;
    }
}
