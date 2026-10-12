package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetCodeIssuedEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId, String code)
        implements DeduplicableDomainEvent {

    public static PasswordResetCodeIssuedEvent issuedFor(Long personId, String code) {
        return new PasswordResetCodeIssuedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), personId, code);
    }
}
