package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.CatalogPriced;

public record CourseAdmissionPreviewResponse(Long courseId, boolean requiresPayment, Money amount)
        implements CatalogPriced<CourseAdmissionPreviewResponse> {

    @Override
    public CourseAdmissionPreviewResponse withoutPrices() {
        return new CourseAdmissionPreviewResponse(courseId, false, null);
    }
}
