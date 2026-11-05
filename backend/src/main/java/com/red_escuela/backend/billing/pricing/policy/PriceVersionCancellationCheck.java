package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;

import java.time.LocalDate;

public record PriceVersionCancellationCheck(PriceVersionStatus status, LocalDate effectiveFrom, LocalDate today, String reason) {
}
