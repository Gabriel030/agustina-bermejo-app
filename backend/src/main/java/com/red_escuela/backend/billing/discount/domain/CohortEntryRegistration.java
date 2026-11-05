package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.YearMonth;
import java.util.Objects;

public record CohortEntryRegistration(EnrollmentReference enrollment, Long studentId, Currency currency, Long configurationVersionId, Percentage discount,
                                      YearMonth period, StatusChangeActor actor, String reason) {

    public CohortEntryRegistration {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(configurationVersionId, "configurationVersionId");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
    }
}
