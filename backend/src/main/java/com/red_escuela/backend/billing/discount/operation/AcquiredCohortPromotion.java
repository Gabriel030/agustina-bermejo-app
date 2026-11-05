package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;

public record AcquiredCohortPromotion(StudentDiscount discount, DiscountDefinition definition, boolean tookLastPlace) {
}
