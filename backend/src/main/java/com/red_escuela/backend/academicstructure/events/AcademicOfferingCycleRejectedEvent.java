package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AcademicOfferingCycleRejectedEvent(String correlationId, Instant occurredAt, Long cycleId, String targetName,
                                                 String termName, String rejectionReason, Long submitterPersonId) implements DomainEvent {

    public static AcademicOfferingCycleRejectedEvent rejectedFor(AcademicOfferingCycle cycle) {
        return new AcademicOfferingCycleRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), cycle.getId(),
                AcademicOfferingCycleTargetNames.of(cycle), cycle.getTerm().getDescription(), cycle.getRejectionReason(),
                cycle.submitterOrCreator().getId());
    }
}
