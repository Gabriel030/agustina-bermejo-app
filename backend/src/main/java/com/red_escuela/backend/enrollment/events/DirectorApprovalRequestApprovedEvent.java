package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record DirectorApprovalRequestApprovedEvent(String correlationId, Instant occurredAt, Long requestId, String description, String decisionNotes,
        Long studentPersonId, Long requestedByPersonId) implements DomainEvent {

    public static DirectorApprovalRequestApprovedEvent approvedFor(Long requestId, String description, String decisionNotes, Long studentPersonId, Long requestedByPersonId) {
        return new DirectorApprovalRequestApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), requestId, description, decisionNotes, studentPersonId,
                requestedByPersonId);
    }
}
