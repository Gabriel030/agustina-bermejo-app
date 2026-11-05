package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentWithdrawnForBillingEvent(UUID eventId, String correlationId, Instant occurredAt, Long studentId, Long programEnrollmentId,
                                                 Long courseEnrollmentId, LocalDate effectiveDate, LocalDate registeredOn, String cause)
        implements DeduplicableDomainEvent {

    public static EnrollmentWithdrawnForBillingEvent withdrawnFor(Long studentId, Long programEnrollmentId, Long courseEnrollmentId, LocalDate effectiveDate,
                                                                  LocalDate registeredOn, String cause) {
        return new EnrollmentWithdrawnForBillingEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), studentId, programEnrollmentId,
                courseEnrollmentId, effectiveDate, registeredOn, cause);
    }
}
