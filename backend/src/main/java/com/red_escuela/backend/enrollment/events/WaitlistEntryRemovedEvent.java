package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record WaitlistEntryRemovedEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String queueName) implements DomainEvent {

    public static WaitlistEntryRemovedEvent of(Long enrollmentId, Long studentPersonId, String queueName) {
        return new WaitlistEntryRemovedEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                queueName);
    }
}
