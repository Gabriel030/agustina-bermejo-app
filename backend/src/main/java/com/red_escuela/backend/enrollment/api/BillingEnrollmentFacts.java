package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Currency;

import java.time.YearMonth;
import java.util.Objects;

public record BillingEnrollmentFacts(Long studentId, Currency currency, Long programId, Long billingTemplateId, YearMonth courseTermStart, YearMonth courseTermEnd) {

    public BillingEnrollmentFacts {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
    }

    public BillingEnrollmentFacts(Long studentId, Currency currency) {
        this(studentId, currency, null, null, null, null);
    }
}
