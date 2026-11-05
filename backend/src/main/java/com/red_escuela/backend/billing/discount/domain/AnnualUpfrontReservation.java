package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record AnnualUpfrontReservation(Long programEnrollmentId, Long studentId, Currency currency, DiscountDefinition definition, ScholarshipPeriodRange range,
                                       Long requestedByPersonId, String reason) {

    public AnnualUpfrontReservation {
        Objects.requireNonNull(programEnrollmentId, "programEnrollmentId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        Objects.requireNonNull(reason, "reason");
    }
}
