package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.entity.PromotionBenefit;

import java.math.BigDecimal;

public record PromotionBenefitResponse(Long id, String feeType, String valueType, BigDecimal valueAmount, Integer invoiceScopeCount) {

    public static PromotionBenefitResponse from(PromotionBenefit benefit) {
        MonetaryAdjustmentValue value = benefit.getValue();
        String valueType = value instanceof MonetaryAdjustmentValue.FixedAmount ? "FIXED" : "PERCENTAGE";
        BigDecimal valueAmount = value instanceof MonetaryAdjustmentValue.FixedAmount fixed
                ? fixed.amount()
                : ((MonetaryAdjustmentValue.PercentageAmount) value).percentage();
        return new PromotionBenefitResponse(benefit.getId(), benefit.getFeeType().name(), valueType, valueAmount, benefit.getInvoiceScopeCount());
    }
}
