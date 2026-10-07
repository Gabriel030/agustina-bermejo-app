package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record AuthorizationRequestedEvent(UUID eventId, String correlationId, Instant occurredAt, Long authorizationRequestId,
                                          String operationType, String requiredRole, String reason) implements DeduplicableDomainEvent {

    public static AuthorizationRequestedEvent requestedFor(Long authorizationRequestId, String operationType, String requiredRole, String reason) {
        return new AuthorizationRequestedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), authorizationRequestId,
                operationType, requiredRole, reason);
    }
}
