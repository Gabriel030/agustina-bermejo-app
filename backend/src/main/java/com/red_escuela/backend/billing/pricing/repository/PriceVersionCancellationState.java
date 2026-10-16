package com.red_escuela.backend.billing.pricing.repository;

import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;

import java.time.LocalDate;

public record PriceVersionCancellationState(Long priceId, PriceVersionStatus status, LocalDate effectiveFrom) {
}
