package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record TermEndClosureOverdueEvent(String correlationId, Instant occurredAt, String sectionKind, Long sectionId,
                                         String sectionLabel, long daysAfterTermEnd) implements DomainEvent {

    public static TermEndClosureOverdueEvent of(String sectionKind, Long sectionId, String sectionLabel, long daysAfterTermEnd) {
        return new TermEndClosureOverdueEvent(CorrelationIds.current(), ApplicationTime.instant(), sectionKind, sectionId,
                sectionLabel, daysAfterTermEnd);
    }
}
