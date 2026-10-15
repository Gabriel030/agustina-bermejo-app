package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.invoice.domain.FeeType;

public interface PromotionBenefitUsage {
    Long getPaymentPlanSelectionId();

    FeeType getFeeType();

    Long getPromotionBenefitId();

    Long getUsed();
}
