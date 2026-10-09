package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.Currency;

import java.math.BigDecimal;
import java.util.Objects;

public record PriceVersionDraft(Currency priceCurrency, Currency baseCurrency, BigDecimal baseAmount, MarketingDiscountType discountType,
                                BigDecimal discountValue, Currency discountCurrency) {

    public PriceVersionDraft {
        Objects.requireNonNull(priceCurrency, "priceCurrency");
        Objects.requireNonNull(baseCurrency, "baseCurrency");
        Objects.requireNonNull(baseAmount, "baseAmount");
        if ((discountType == null) != (discountValue == null)) {
            throw new IllegalArgumentException("A marketing discount needs both its type and its value");
        }
    }

    public static PriceVersionDraft withoutDiscount(Currency priceCurrency, Currency baseCurrency, BigDecimal baseAmount) {
        return new PriceVersionDraft(priceCurrency, baseCurrency, baseAmount, null, null, null);
    }

    public static PriceVersionDraft withPercentageDiscount(Currency priceCurrency, Currency baseCurrency, BigDecimal baseAmount, BigDecimal points) {
        return new PriceVersionDraft(priceCurrency, baseCurrency, baseAmount, MarketingDiscountType.PERCENTAGE, points, null);
    }

    public static PriceVersionDraft withFixedDiscount(Currency priceCurrency, Currency baseCurrency, BigDecimal baseAmount, BigDecimal discountAmount,
                                                      Currency discountCurrency) {
        return new PriceVersionDraft(priceCurrency, baseCurrency, baseAmount, MarketingDiscountType.FIXED, discountAmount, discountCurrency);
    }

    public boolean hasDiscountOfType(MarketingDiscountType type) {
        return discountType == type;
    }
}
