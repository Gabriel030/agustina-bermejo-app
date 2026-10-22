package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record CohortPromotionAcquisition(EnrollmentReference enrollment, Long studentId, Currency currency, DiscountDefinition definition,
                                         ScholarshipPeriodRange referenceRange, Long actorPersonId, String reason) {

    public CohortPromotionAcquisition {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(referenceRange, "referenceRange");
        Objects.requireNonNull(actorPersonId, "actorPersonId");
        Objects.requireNonNull(reason, "reason");
    }
}
