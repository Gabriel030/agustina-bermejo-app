package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record EmailVerificationIssuedEvent(String correlationId, Instant occurredAt, Long personId, String verificationLink)
        implements DomainEvent {

    public static EmailVerificationIssuedEvent issuedFor(Long personId, String verificationLink) {
        return new EmailVerificationIssuedEvent(CorrelationIds.current(), ApplicationTime.instant(), personId, verificationLink);
    }
}
