package com.red_escuela.backend.billing.pricing.dto;

import org.springframework.lang.Nullable;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.shared.domain.Currency;

import java.time.LocalDate;

public record PriceSummaryResponse(Long priceId, @Nullable Long programId, @Nullable Long courseId, FeeType feeType, String description, Currency currency,
                                   @Nullable PriceVersionResponse currentVersion, @Nullable PriceVersionResponse nextScheduledVersion, @Nullable PriceVersionResponse openProposal) {

    public static PriceSummaryResponse from(Price price, LocalDate today) {
        return new PriceSummaryResponse(price.getId(), price.getProgramId(), price.getCourseId(), price.getFeeType(), price.getDescription(), price.getCurrency(),
                price.findVersionEffectiveOn(today).map(PriceVersionResponse::from).orElse(null),
                price.findNextScheduledVersionAfter(today).map(PriceVersionResponse::from).orElse(null),
                price.findOpenProposal().map(PriceVersionResponse::from).orElse(null));
    }
}
