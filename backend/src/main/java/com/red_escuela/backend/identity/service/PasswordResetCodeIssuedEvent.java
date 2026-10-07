package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;

import java.time.Instant;

public record PasswordResetCodeIssuedEvent(String correlationId, Instant occurredAt, Long personId, String code)
        implements DomainEvent {

    public static PasswordResetCodeIssuedEvent issuedFor(Long personId, String code) {
        return new PasswordResetCodeIssuedEvent(CorrelationIds.current(), Instant.now(), personId, code);
    }
}
