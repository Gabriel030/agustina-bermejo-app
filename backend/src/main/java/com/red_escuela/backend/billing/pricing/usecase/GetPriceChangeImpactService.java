package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.billing.pricing.service.PriceChangeImpactCalculator;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

@UseCase
public class GetPriceChangeImpactService implements GetPriceChangeImpactUseCase {
    private final PriceChangeImpactCalculator calculator;

    public GetPriceChangeImpactService(PriceChangeImpactCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public PriceChangeImpact getPriceChangeImpact(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom) {
        return calculator.calculatePriceChangeImpact(priceId, baseAmount, marketingDiscount, effectiveFrom);
    }
}
