package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentClosedWhileSuspendedEvent(UUID eventId, String correlationId, Instant occurredAt, EnrollmentKind kind, Long enrollmentId,
                                                  LocalDate businessDate) implements DeduplicableDomainEvent {

    public static EnrollmentClosedWhileSuspendedEvent closedOn(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate) {
        return new EnrollmentClosedWhileSuspendedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), kind, enrollmentId, businessDate);
    }
}
