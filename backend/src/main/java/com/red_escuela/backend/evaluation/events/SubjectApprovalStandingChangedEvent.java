package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SubjectApprovalStandingChangedEvent(String correlationId, Instant occurredAt, Long programEnrollmentId,
                                                  Long planId, Long subjectId, boolean approved) implements DomainEvent {

    public static SubjectApprovalStandingChangedEvent changedFor(Long programEnrollmentId, Long planId, Long subjectId,
                                                                 boolean approved) {
        return new SubjectApprovalStandingChangedEvent(CorrelationIds.current(), ApplicationTime.instant(),
                programEnrollmentId, planId, subjectId, approved);
    }
}
