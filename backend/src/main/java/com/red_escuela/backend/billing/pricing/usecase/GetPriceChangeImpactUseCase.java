package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public interface GetPriceChangeImpactUseCase {
    PriceChangeImpact getPriceChangeImpact(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom);
}
