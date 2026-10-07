package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record CollectionsPolicyResponse(
        Long id, Integer gracePeriodDays, Percentage surchargePercent,
        Integer suspensionAfterDays, Integer suspensionAfterInvoiceCount,
        LocalDate effectiveFrom) {

    public static CollectionsPolicyResponse from(CollectionsPolicy policy) {
        return new CollectionsPolicyResponse(
                policy.getId(), policy.getGracePeriodDays(), policy.getSurchargeRate(),
                policy.getSuspensionAfterDays(), policy.getSuspensionAfterInvoiceCount(),
                policy.getEffectiveFrom());
    }
}
