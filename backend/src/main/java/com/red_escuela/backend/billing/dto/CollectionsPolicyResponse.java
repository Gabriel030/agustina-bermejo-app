package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CollectionsPolicyResponse(
        Long id, Integer gracePeriodDays, BigDecimal surchargePercentage,
        Integer suspensionAfterDays, Integer suspensionAfterInvoiceCount,
        Integer withdrawalAfterDays, Integer withdrawalAfterInvoiceCount, LocalDate effectiveFrom) {

    public static CollectionsPolicyResponse from(CollectionsPolicy policy) {
        return new CollectionsPolicyResponse(
                policy.getId(), policy.getGracePeriodDays(), policy.getSurchargePercentage(),
                policy.getSuspensionAfterDays(), policy.getSuspensionAfterInvoiceCount(),
                policy.getWithdrawalAfterDays(), policy.getWithdrawalAfterInvoiceCount(), policy.getEffectiveFrom());
    }
}
