package com.red_escuela.backend.billing.discount.domain;

import org.springframework.lang.Nullable;

public record StudentDiscountLabels(@Nullable String studentName, @Nullable String enrollmentName, @Nullable String definitionName, @Nullable DiscountDefinitionType definitionType) {

    public static StudentDiscountLabels none() {
        return new StudentDiscountLabels(null, null, null, null);
    }
}
