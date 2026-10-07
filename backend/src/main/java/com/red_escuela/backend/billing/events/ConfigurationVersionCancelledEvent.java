package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ConfigurationVersionCancelledEvent(UUID eventId, String correlationId, Instant occurredAt, Long configurationVersionId, String scope,
                                                 String topicKey, Long proposedByPersonId, LocalDate effectiveFrom, String reason) implements DeduplicableDomainEvent {

    public static ConfigurationVersionCancelledEvent cancelledFor(Long configurationVersionId, String scope, String topicKey, Long proposedByPersonId,
                                                                  LocalDate effectiveFrom, String reason) {
        return new ConfigurationVersionCancelledEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), configurationVersionId, scope,
                topicKey, proposedByPersonId, effectiveFrom, reason);
    }
}
