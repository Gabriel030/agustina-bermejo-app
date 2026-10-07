package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.Percentage;

import java.time.LocalDate;

public record CreateLatePaymentSettingsCommand(int gracePeriodDays, Percentage surchargeRate, int suspensionAfterDays,
                                               int suspensionAfterInvoiceCount, LocalDate effectiveFrom, Long requestedByPersonId) {
}
