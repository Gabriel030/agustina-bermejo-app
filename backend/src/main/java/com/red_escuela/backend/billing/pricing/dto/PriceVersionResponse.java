package com.red_escuela.backend.billing.pricing.dto;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record PriceVersionResponse(Long id, Long priceId, PriceVersionStatus status, LocalDate effectiveFrom, NonNegativeMoney baseAmount,
                                   MarketingDiscountType marketingDiscountType, Percentage marketingDiscountPercent,
                                   NonNegativeMoney marketingDiscountFixedAmount, NonNegativeMoney marketingDiscountAmount, NonNegativeMoney finalAmount,
                                   String changeReason, boolean directPublication, Long proposedById, Long decidedById,
                                   Long cancelledById, String cancellationReason) {

    public static PriceVersionResponse from(PriceVersion version) {
        return new PriceVersionResponse(version.getId(), version.getPrice().getId(), version.getStatus(), version.getEffectiveFrom(), version.getBaseAmount(),
                version.getMarketingDiscountType(),
                version.getMarketingDiscountPercentage(),
                version.getMarketingDiscountFixedAmount(), version.marketingDiscountAmount(), version.finalAmount(), version.getChangeReason(),
                version.isDirectPublication(), version.getProposedById(), version.getDecidedById(), version.getCancelledById(), version.getCancellationReason());
    }
}
