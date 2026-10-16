package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.PriceVersion;

public interface CancelScheduledPriceVersionUseCase {
    PriceVersion cancelScheduledVersion(Long priceVersionId, String reason);
}
