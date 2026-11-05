package com.red_escuela.backend.billing.annualupfront.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record AnnualUpfrontRequestRegistration(Long programEnrollmentId, Long lectiveYearTermId, Long invoiceId, Long personId, LocalDateTime requestedAt,
                                               AnnualUpfrontTerms terms) {

    public AnnualUpfrontRequestRegistration {
        Objects.requireNonNull(programEnrollmentId, "programEnrollmentId");
        Objects.requireNonNull(lectiveYearTermId, "lectiveYearTermId");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(terms, "terms");
    }
}
