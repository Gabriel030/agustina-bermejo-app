package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;

import java.time.LocalDate;
import java.util.Optional;

public record AdvancePriceCheck(FeeType feeType, Integer lectiveYear, LocalDate effectiveFrom, Optional<LocalDate> regularEmissionStart) {
}
