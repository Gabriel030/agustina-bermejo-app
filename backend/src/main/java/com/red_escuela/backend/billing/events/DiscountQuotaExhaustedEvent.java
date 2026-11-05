package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record DiscountQuotaExhaustedEvent(UUID eventId, String correlationId, Instant occurredAt, Long discountDefinitionId, String discountName,
                                          int quota) implements DeduplicableDomainEvent {

    public static DiscountQuotaExhaustedEvent exhaustedFor(Long discountDefinitionId, String discountName, int quota) {
        return new DiscountQuotaExhaustedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), discountDefinitionId, discountName, quota);
    }
}
