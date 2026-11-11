package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ClosureExceptionGrantedEvent(String correlationId, Instant occurredAt, Long closureId, String sectionKind, Long sectionId, Long enrollmentId, String reason) implements DomainEvent {

    public static ClosureExceptionGrantedEvent of(Long closureId, String sectionKind, Long sectionId, Long enrollmentId, String reason) {
        return new ClosureExceptionGrantedEvent(CorrelationIds.current(), ApplicationTime.instant(), closureId, sectionKind, sectionId, enrollmentId, reason);
    }
}
