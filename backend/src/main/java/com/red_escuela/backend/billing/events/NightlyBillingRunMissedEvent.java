package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NightlyBillingRunMissedEvent(UUID eventId, String correlationId, Instant occurredAt, LocalDate businessDate) implements DeduplicableDomainEvent {

    public static NightlyBillingRunMissedEvent missedOn(LocalDate businessDate) {
        UUID oneIdentityPerBusinessDate = UUID.nameUUIDFromBytes(("NightlyBillingRunMissed:" + businessDate).getBytes(StandardCharsets.UTF_8));
        return new NightlyBillingRunMissedEvent(oneIdentityPerBusinessDate, CorrelationIds.current(), ApplicationTime.instant(), businessDate);
    }
}
