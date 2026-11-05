package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;

public record PromotionBenefitResponse(Long id, String feeType, MonetaryAdjustment value, Integer invoiceScopeCount) {

    public static PromotionBenefitResponse from(DiscountDefinition definition) {
        boolean monthly = definition.getApplicableInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT;
        return new PromotionBenefitResponse(definition.getId(), definition.getApplicableInvoiceType().name(), definition.getCalculation(),
                monthly ? definition.getInstallmentsTotal() : null);
    }
}
