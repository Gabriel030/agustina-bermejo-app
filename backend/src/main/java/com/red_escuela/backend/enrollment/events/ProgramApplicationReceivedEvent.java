package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record ProgramApplicationReceivedEvent(UUID eventId, String correlationId, Instant occurredAt, Long applicationId,
                                              String studentFullName, String programName) implements DeduplicableDomainEvent {

    public static ProgramApplicationReceivedEvent receivedFor(Long applicationId, String studentFullName, String programName) {
        return new ProgramApplicationReceivedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                applicationId, studentFullName, programName);
    }
}
