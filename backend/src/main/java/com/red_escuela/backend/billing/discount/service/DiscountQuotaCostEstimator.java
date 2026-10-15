package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class DiscountQuotaCostEstimator {
    private final PriceReader priceReader;

    public DiscountQuotaCostEstimator(PriceReader priceReader) {
        this.priceReader = priceReader;
    }

    public Optional<NonNegativeMoney> estimateQuotaCostOn(DiscountDefinition definition, LocalDate today) {
        return findFinalPriceOn(definition, today).flatMap(definition::estimateQuotaCost);
    }

    private Optional<NonNegativeMoney> findFinalPriceOn(DiscountDefinition definition, LocalDate today) {
        return definition.findPricedOffering().flatMap(offering -> definition.findPricedFeeType()
                .flatMap(feeType -> priceReader.findPriceVersionEffectiveOn(offering, feeType, today)))
                .map(PriceVersionResolution::finalAmount);
    }
}
