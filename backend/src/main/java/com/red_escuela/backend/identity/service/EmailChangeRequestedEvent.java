package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record EmailChangeRequestedEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId,
                                        String previousEmail, String newEmail, String verificationLink)
        implements DeduplicableDomainEvent {

    public static EmailChangeRequestedEvent requestedFor(Long personId, String previousEmail, String newEmail,
                                                         String verificationLink) {
        return new EmailChangeRequestedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                personId, previousEmail, newEmail, verificationLink);
    }
}
