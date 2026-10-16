package com.red_escuela.backend.billing.pricing.policy;

public record CancelPriceVersionCommand(Long priceVersionId, String reason, Long cancelledByPersonId) {
}
