package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ClosureReopenedEvent(String correlationId, Instant occurredAt, Long closureId, String sectionKind, Long sectionId, String reportNumber, int reportVersion, String reason) implements DomainEvent {

    public static ClosureReopenedEvent of(Long closureId, String sectionKind, Long sectionId, String reportNumber, int reportVersion, String reason) {
        return new ClosureReopenedEvent(CorrelationIds.current(), ApplicationTime.instant(), closureId, sectionKind, sectionId, reportNumber, reportVersion, reason);
    }
}
