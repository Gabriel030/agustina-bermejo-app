package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record PriceVersionResolution(Long priceVersionId, NonNegativeMoney listAmount, NonNegativeMoney marketingDiscountAmount, NonNegativeMoney finalAmount) {
}
