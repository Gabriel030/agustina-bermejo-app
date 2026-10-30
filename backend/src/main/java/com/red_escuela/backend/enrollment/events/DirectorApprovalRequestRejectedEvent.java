package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record DirectorApprovalRequestRejectedEvent(String correlationId, Instant occurredAt, Long requestId, String description, String decisionNotes,
        Long studentPersonId, Long requestedByPersonId) implements DomainEvent {

    public static DirectorApprovalRequestRejectedEvent rejectedFor(Long requestId, String description, String decisionNotes, Long studentPersonId, Long requestedByPersonId) {
        return new DirectorApprovalRequestRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), requestId, description, decisionNotes, studentPersonId,
                requestedByPersonId);
    }
}
