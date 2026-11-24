package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record WaitlistEntryPausedEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String queueName, String conflictingSectionName) implements DomainEvent {

    public static WaitlistEntryPausedEvent of(Long enrollmentId, Long studentPersonId, String queueName, String conflictingSectionName) {
        return new WaitlistEntryPausedEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                queueName, conflictingSectionName);
    }
}
