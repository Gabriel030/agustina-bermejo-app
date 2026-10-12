package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record ConfigurationChangeDecidedEvent(UUID eventId, String correlationId, Instant occurredAt, Long configurationVersionId, String scope,
                                              String topicKey, Long proposedByPersonId, String decision, String reason) implements DeduplicableDomainEvent {

    public static ConfigurationChangeDecidedEvent approvedFor(Long configurationVersionId, String scope, String topicKey, Long proposedByPersonId) {
        return new ConfigurationChangeDecidedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), configurationVersionId, scope,
                topicKey, proposedByPersonId, "APPROVED", null);
    }

    public static ConfigurationChangeDecidedEvent rejectedFor(Long configurationVersionId, String scope, String topicKey, Long proposedByPersonId, String reason) {
        return new ConfigurationChangeDecidedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), configurationVersionId, scope,
                topicKey, proposedByPersonId, "REJECTED", reason);
    }
}
