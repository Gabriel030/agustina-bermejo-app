package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ProgramSectionStatusChangedEvent(String correlationId, Instant occurredAt, Long programEnrollmentId,
                                               Long sectionEnrollmentId, boolean statusChanged,
                                               boolean approvedOutcomeChanged) implements DomainEvent {

    public static ProgramSectionStatusChangedEvent changed(Long programEnrollmentId, Long sectionEnrollmentId,
                                                           boolean statusChanged, boolean approvedOutcomeChanged) {
        return new ProgramSectionStatusChangedEvent(CorrelationIds.current(), ApplicationTime.instant(), programEnrollmentId,
                sectionEnrollmentId, statusChanged, approvedOutcomeChanged);
    }
}
