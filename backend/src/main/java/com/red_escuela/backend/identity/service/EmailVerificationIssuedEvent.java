package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record EmailVerificationIssuedEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId, String verificationLink)
        implements DeduplicableDomainEvent {

    public static EmailVerificationIssuedEvent issuedFor(Long personId, String verificationLink) {
        return new EmailVerificationIssuedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), personId, verificationLink);
    }
}
