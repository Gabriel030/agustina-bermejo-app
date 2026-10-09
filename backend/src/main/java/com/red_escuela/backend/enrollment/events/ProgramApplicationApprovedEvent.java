package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record ProgramApplicationApprovedEvent(UUID eventId, String correlationId, Instant occurredAt, Long applicationId,
                                              Long studentPersonId, String programName) implements DeduplicableDomainEvent {

    public static ProgramApplicationApprovedEvent approvedFor(Long applicationId, Long studentPersonId, String programName) {
        return new ProgramApplicationApprovedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                applicationId, studentPersonId, programName);
    }
}
