package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record LatePaymentSettingsResponse(
        Long id, Integer gracePeriodDays, Percentage surchargePercent,
        Integer suspensionAfterDays, Integer suspensionAfterInvoiceCount,
        LocalDate effectiveFrom) {

    public static LatePaymentSettingsResponse from(ConfigurationVersion version) {
        LatePaymentTerms terms = (LatePaymentTerms) version.values();
        return new LatePaymentSettingsResponse(
                version.getId(), terms.gracePeriodDays(), terms.dailyLateFeeRate(),
                terms.suspensionAfterDays(), terms.suspensionAfterInvoiceCount(),
                version.getEffectiveFrom());
    }
}
