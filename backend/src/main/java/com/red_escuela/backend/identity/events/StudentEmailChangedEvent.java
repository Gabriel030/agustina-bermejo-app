package com.red_escuela.backend.identity.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record StudentEmailChangedEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId,
                                       String previousEmail, String newEmail, String verificationLink)
        implements DeduplicableDomainEvent {

    public static StudentEmailChangedEvent changedFor(Long personId, String previousEmail, String newEmail,
                                                      String verificationLink) {
        return new StudentEmailChangedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                personId, previousEmail, newEmail, verificationLink);
    }
}
