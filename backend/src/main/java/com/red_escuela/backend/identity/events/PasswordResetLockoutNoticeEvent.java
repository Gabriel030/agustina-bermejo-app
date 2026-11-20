package com.red_escuela.backend.identity.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetLockoutNoticeEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId)
        implements DeduplicableDomainEvent {

    public static PasswordResetLockoutNoticeEvent forPerson(Long personId) {
        return new PasswordResetLockoutNoticeEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), personId);
    }
}
