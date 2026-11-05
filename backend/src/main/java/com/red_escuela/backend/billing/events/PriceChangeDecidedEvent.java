package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceChangeDecidedEvent(UUID eventId, String correlationId, Instant occurredAt, Long priceVersionId, Long priceId, String priceDescription,
                                      LocalDate effectiveFrom, Long proposedByPersonId, String decision, String reason) implements DeduplicableDomainEvent {

    public static PriceChangeDecidedEvent approvedFor(Long priceVersionId, Long priceId, String priceDescription, LocalDate effectiveFrom, Long proposedByPersonId) {
        return new PriceChangeDecidedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), priceVersionId, priceId, priceDescription,
                effectiveFrom, proposedByPersonId, "APPROVED", null);
    }

    public static PriceChangeDecidedEvent rejectedFor(Long priceVersionId, Long priceId, String priceDescription, LocalDate effectiveFrom, Long proposedByPersonId,
                                                      String reason) {
        return new PriceChangeDecidedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), priceVersionId, priceId, priceDescription,
                effectiveFrom, proposedByPersonId, "REJECTED", reason);
    }
}
