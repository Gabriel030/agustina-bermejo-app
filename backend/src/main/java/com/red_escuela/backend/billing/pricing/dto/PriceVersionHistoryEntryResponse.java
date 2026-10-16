package com.red_escuela.backend.billing.pricing.dto;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PriceVersionHistoryEntryResponse(Long id, Long priceId, PriceVersionStatus status, LocalDate effectiveFrom, NonNegativeMoney baseAmount,
                                               MarketingDiscountType marketingDiscountType, Percentage marketingDiscountPercent,
                                               NonNegativeMoney marketingDiscountFixedAmount, NonNegativeMoney marketingDiscountAmount, NonNegativeMoney finalAmount,
                                               String changeReason, boolean directPublication, boolean initialVersion, Long previousVersionId,
                                               Long proposedById, Instant proposedAt, Long decidedById, Instant decidedAt, String decisionReason,
                                               Long cancelledById, Instant cancelledAt, String cancellationReason,
                                               List<PriceVersionStatusChangeResponse> statusChanges) {

    public static PriceVersionHistoryEntryResponse from(PriceVersion version) {
        return new PriceVersionHistoryEntryResponse(version.getId(), version.getPrice().getId(), version.getStatus(), version.getEffectiveFrom(),
                version.getBaseAmount(), version.getMarketingDiscountType(), version.getMarketingDiscountPercentage(), version.getMarketingDiscountFixedAmount(),
                version.marketingDiscountAmount(), version.finalAmount(), version.getChangeReason(), version.isDirectPublication(), version.isInitialVersion(),
                version.getPreviousVersionId(), version.getProposedById(), version.getProposedAt(), version.getDecidedById(), version.getDecidedAt(),
                version.getDecisionReason(), version.getCancelledById(), version.getCancelledAt(), version.getCancellationReason(),
                version.getStatusChanges().stream().map(PriceVersionStatusChangeResponse::from).toList());
    }
}
