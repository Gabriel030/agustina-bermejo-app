package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.PromotionAlreadyClosedException;
import com.red_escuela.backend.billing.discount.exception.PromotionNotFoundException;

import java.util.Optional;

public record PromotionClosureTarget(DiscountDefinition definition) {

    public static PromotionClosureTarget requireOpenPromotion(Long promotionId, Optional<DiscountDefinition> found) {
        DiscountDefinition definition = found.filter(candidate -> candidate.getDiscountType() == DiscountDefinitionType.COHORT_PROMOTION)
                .orElseThrow(() -> new PromotionNotFoundException(promotionId));
        if (definition.getStatus() == DiscountDefinitionStatus.CLOSED) {
            throw new PromotionAlreadyClosedException(promotionId);
        }
        return new PromotionClosureTarget(definition);
    }
}
