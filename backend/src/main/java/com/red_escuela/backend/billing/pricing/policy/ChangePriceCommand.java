package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.operation.DirectorApprovable;
import com.red_escuela.backend.billing.operation.ResumedFromAuthorizationRequest;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record ChangePriceCommand(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom,
                                 String changeReason, Long requestedByPersonId, boolean requestedByDirector, Long authorizationRequestId)
        implements DirectorApprovable, ResumedFromAuthorizationRequest<ChangePriceCommand> {

    public ChangePriceCommand(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason,
                              Long requestedByPersonId, boolean requestedByDirector) {
        this(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, requestedByPersonId, requestedByDirector, null);
    }

    @Override
    public ChangePriceCommand resumedFromAuthorizationRequest(Long authorizationRequestId) {
        return new ChangePriceCommand(priceId, baseAmount, marketingDiscount, effectiveFrom, changeReason, requestedByPersonId, requestedByDirector, authorizationRequestId);
    }

    public PriceVersionDraft toDraft(Currency priceCurrency) {
        if (marketingDiscount == null) {
            return PriceVersionDraft.withoutDiscount(priceCurrency, baseAmount.value());
        }
        return marketingDiscount.type() == MarketingDiscountType.PERCENTAGE
                ? PriceVersionDraft.withPercentageDiscount(priceCurrency, baseAmount.value(), marketingDiscount.percentage())
                : PriceVersionDraft.withFixedDiscount(priceCurrency, baseAmount.value(), marketingDiscount.fixedAmount().value());
    }

    @Override
    public String authorizationReason() {
        return "Cambio de precio (id " + priceId + ") con vigencia desde " + effectiveFrom + ": " + changeReason;
    }

    @Override
    public LocalDate authorizationExpiresOn() {
        return effectiveFrom;
    }
}
