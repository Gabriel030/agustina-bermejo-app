package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CourseCapacityChangedEvent(String correlationId, Instant occurredAt, Long courseId, Integer previousCapacity,
                                          Integer newCapacity) implements DomainEvent {

    public static CourseCapacityChangedEvent of(Long courseId, Integer previousCapacity, Integer newCapacity) {
        return new CourseCapacityChangedEvent(CorrelationIds.current(), ApplicationTime.instant(), courseId, previousCapacity,
                newCapacity);
    }
}
