package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ClosureApprovedEvent(String correlationId, Instant occurredAt, Long closureId, String sectionKind, Long sectionId, String reportNumber, int reportVersion, boolean closedWithoutTeacherConfirmation) implements DomainEvent {

    public static ClosureApprovedEvent of(Long closureId, String sectionKind, Long sectionId, String reportNumber, int reportVersion, boolean closedWithoutTeacherConfirmation) {
        return new ClosureApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), closureId, sectionKind, sectionId, reportNumber, reportVersion, closedWithoutTeacherConfirmation);
    }
}
