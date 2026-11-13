package com.red_escuela.backend.identity.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record StudentReactivatedEvent(String correlationId, Instant occurredAt, Long studentId, Long personId) implements DomainEvent {

    public static StudentReactivatedEvent of(Long studentId, Long personId) {
        return new StudentReactivatedEvent(CorrelationIds.current(), ApplicationTime.instant(), studentId, personId);
    }
}
