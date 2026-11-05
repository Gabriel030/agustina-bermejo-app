package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import org.springframework.lang.Nullable;

public record StudentDiscountSearch(@Nullable StudentDiscountStatus status, @Nullable DiscountType type, @Nullable DiscountDefinitionType definitionType,
                                    @Nullable Long discountDefinitionId, @Nullable Long studentId, @Nullable Long programEnrollmentId, @Nullable Long courseEnrollmentId) {
}
