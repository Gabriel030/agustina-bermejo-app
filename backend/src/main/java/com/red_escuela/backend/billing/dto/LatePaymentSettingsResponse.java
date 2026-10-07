package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.LatePaymentSettings;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record LatePaymentSettingsResponse(
        Long id, Integer gracePeriodDays, Percentage surchargePercent,
        Integer suspensionAfterDays, Integer suspensionAfterInvoiceCount,
        LocalDate effectiveFrom) {

    public static LatePaymentSettingsResponse from(LatePaymentSettings policy) {
        return new LatePaymentSettingsResponse(
                policy.getId(), policy.getGracePeriodDays(), policy.getSurchargeRate(),
                policy.getSuspensionAfterDays(), policy.getSuspensionAfterInvoiceCount(),
                policy.getEffectiveFrom());
    }
}
