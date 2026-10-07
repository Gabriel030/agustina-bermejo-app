package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDate;

public record LatePaymentSettingsValues(int gracePeriodDays, Percentage surchargeRate, int suspensionAfterDays,
                                        int suspensionAfterInvoiceCount, LocalDate effectiveFrom, LocalDate today) implements EffectiveDated {
}
