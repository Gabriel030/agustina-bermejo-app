package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OverdueContactListUpdatedEvent(UUID eventId, String correlationId, Instant occurredAt, LocalDate businessDate, int count, int newCount)
        implements DeduplicableDomainEvent {

    public static OverdueContactListUpdatedEvent updatedOn(LocalDate businessDate, int count, int newCount) {
        UUID eventIdOfTheDay = UUID.nameUUIDFromBytes(("overdue-contact-list:" + businessDate).getBytes(StandardCharsets.UTF_8));
        return new OverdueContactListUpdatedEvent(eventIdOfTheDay, CorrelationIds.current(), ApplicationTime.instant(), businessDate, count, newCount);
    }
}
