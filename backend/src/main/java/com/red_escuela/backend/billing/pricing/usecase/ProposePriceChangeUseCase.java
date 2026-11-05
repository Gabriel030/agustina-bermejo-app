package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public interface ProposePriceChangeUseCase {
    BillingOperationOutcome<PriceVersion> proposeOrPublishPriceVersion(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason);
}
