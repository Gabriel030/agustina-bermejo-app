package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountQuotaCostEstimate;
import com.red_escuela.backend.billing.discount.domain.DiscountQuotaCostStatus;
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

    public DiscountQuotaCostEstimate estimateQuotaCostOn(DiscountDefinition definition, LocalDate today) {
        if (!definition.hasQuota()) {
            return DiscountQuotaCostEstimate.withoutCost(DiscountQuotaCostStatus.NO_QUOTA);
        }
        boolean priceable = definition.hasCalculableQuotaCost() && definition.findPricedOffering().isPresent() && definition.findPricedFeeType().isPresent();
        if (!priceable) {
            return DiscountQuotaCostEstimate.withoutCost(DiscountQuotaCostStatus.NOT_APPLICABLE);
        }
        return findFinalPriceOn(definition, today)
                .flatMap(definition::estimateQuotaCost)
                .map(DiscountQuotaCostEstimate::estimated)
                .orElseGet(() -> DiscountQuotaCostEstimate.withoutCost(DiscountQuotaCostStatus.NO_EFFECTIVE_PRICE));
    }

    private Optional<NonNegativeMoney> findFinalPriceOn(DiscountDefinition definition, LocalDate today) {
        return definition.findPricedOffering().flatMap(offering -> definition.findPricedFeeType()
                .flatMap(feeType -> priceReader.findPriceVersionEffectiveOn(offering, feeType, today)))
                .map(PriceVersionResolution::finalAmount);
    }
}
