package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.configuration.policy.EffectiveDated;

import java.time.LocalDate;

public record PriceChangeCheck(LocalDate effectiveFrom, LocalDate today, String changeReason) implements EffectiveDated {
}
