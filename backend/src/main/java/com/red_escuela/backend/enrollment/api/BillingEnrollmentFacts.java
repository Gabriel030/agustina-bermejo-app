package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record BillingEnrollmentFacts(Long studentId, Currency currency) {

    public BillingEnrollmentFacts {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
    }
}
