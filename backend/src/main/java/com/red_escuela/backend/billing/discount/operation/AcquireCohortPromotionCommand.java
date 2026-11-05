package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.shared.pricing.OfferingReference;

import java.time.LocalDate;
import java.util.Objects;

public record AcquireCohortPromotionCommand(EnrollmentReference enrollment, OfferingReference offering, Long programCohortId, LocalDate enrollmentDate,
                                            Long actorPersonId) {

    public AcquireCohortPromotionCommand {
        Objects.requireNonNull(enrollment, "enrollment");
        Objects.requireNonNull(offering, "offering");
        Objects.requireNonNull(enrollmentDate, "enrollmentDate");
        Objects.requireNonNull(actorPersonId, "actorPersonId");
    }
}
