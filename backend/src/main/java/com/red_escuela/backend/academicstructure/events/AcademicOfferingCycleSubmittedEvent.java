package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AcademicOfferingCycleSubmittedEvent(String correlationId, Instant occurredAt, Long cycleId, String targetName,
                                                  String termName) implements DomainEvent {

    public static AcademicOfferingCycleSubmittedEvent submittedFor(AcademicOfferingCycle cycle) {
        return new AcademicOfferingCycleSubmittedEvent(CorrelationIds.current(), ApplicationTime.instant(), cycle.getId(),
                AcademicOfferingCycleTargetNames.of(cycle), cycle.getTerm().getDescription());
    }
}
