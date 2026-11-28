package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ProgramGradeCorrectedEvent(String correlationId, Instant occurredAt, Long programEnrollmentId, Long studentPersonId)
        implements DomainEvent {

    public static ProgramGradeCorrectedEvent correctedFor(Long programEnrollmentId, Long studentPersonId) {
        return new ProgramGradeCorrectedEvent(CorrelationIds.current(), ApplicationTime.instant(), programEnrollmentId, studentPersonId);
    }
}
