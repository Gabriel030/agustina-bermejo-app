package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record AuthorizationRequestExpiredEvent(UUID eventId, String correlationId, Instant occurredAt, Long authorizationRequestId,
                                               Long requestedByPersonId, String operationType) implements DeduplicableDomainEvent {

    public static AuthorizationRequestExpiredEvent expiredFor(Long authorizationRequestId, Long requestedByPersonId, String operationType) {
        return new AuthorizationRequestExpiredEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), authorizationRequestId,
                requestedByPersonId, operationType);
    }
}
