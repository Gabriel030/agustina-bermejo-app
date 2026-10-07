package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LatePaymentSettingsCreatedEvent(UUID eventId, String correlationId, Instant occurredAt, Long collectionsPolicyId,
                                              LocalDate effectiveFrom, Long createdByPersonId) implements DeduplicableDomainEvent {

    public static LatePaymentSettingsCreatedEvent createdFor(Long collectionsPolicyId, LocalDate effectiveFrom, Long createdByPersonId) {
        return new LatePaymentSettingsCreatedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), collectionsPolicyId,
                effectiveFrom, createdByPersonId);
    }
}
