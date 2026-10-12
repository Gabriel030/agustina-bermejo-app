package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;

public record PromotionBenefitResponse(Long id, String feeType, MonetaryAdjustment value, Integer invoiceScopeCount) {
    public static PromotionBenefitResponse from(PromotionBenefit benefit) {
        return new PromotionBenefitResponse(benefit.getId(), benefit.getFeeType().name(), benefit.getValue(), benefit.getInvoiceScopeCount());
    }
}
