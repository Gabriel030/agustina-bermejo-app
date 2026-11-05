package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record DiscountDefinitionCreatedEvent(UUID eventId, String correlationId, Instant occurredAt, Long discountDefinitionId, String discountType,
                                             Long createdByPersonId, boolean directPublication) implements DeduplicableDomainEvent {

    public static DiscountDefinitionCreatedEvent createdFor(Long discountDefinitionId, String discountType, Long createdByPersonId, boolean directPublication) {
        return new DiscountDefinitionCreatedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), discountDefinitionId, discountType,
                createdByPersonId, directPublication);
    }
}
