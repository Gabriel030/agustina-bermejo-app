package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AcademicOfferingCycleApprovedEvent(String correlationId, Instant occurredAt, Long cycleId, String targetName,
                                                 String termName, Long submitterPersonId) implements DomainEvent {

    public static AcademicOfferingCycleApprovedEvent approvedFor(AcademicOfferingCycle cycle) {
        return new AcademicOfferingCycleApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), cycle.getId(),
                AcademicOfferingCycleTargetNames.of(cycle), cycle.getTerm().getDescription(), cycle.submitterOrCreator().getId());
    }
}
