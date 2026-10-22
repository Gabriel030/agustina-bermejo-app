package com.red_escuela.backend.billing.discount.dto;

import java.time.YearMonth;

public record LostPromotionBenefitResponse(
        Long promotionRedemptionId, String promotionDescription, String studentName, String feeType,
        int used, Integer scopeCount, int remaining, YearMonth cutOffAt) {
}
