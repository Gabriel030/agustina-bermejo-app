package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.CatalogPriced;

public record CoursePricingResponse(Long courseId, Money cuotaMensual) implements CatalogPriced<CoursePricingResponse> {

    @Override
    public CoursePricingResponse withoutPrices() {
        return new CoursePricingResponse(courseId, null);
    }
}
