package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SectionCapacityChangedEvent(String correlationId, Instant occurredAt, Long sectionId, Integer previousCapacity,
                                          Integer newCapacity) implements DomainEvent {

    public static SectionCapacityChangedEvent of(Long sectionId, Integer previousCapacity, Integer newCapacity) {
        return new SectionCapacityChangedEvent(CorrelationIds.current(), ApplicationTime.instant(), sectionId, previousCapacity,
                newCapacity);
    }
}
