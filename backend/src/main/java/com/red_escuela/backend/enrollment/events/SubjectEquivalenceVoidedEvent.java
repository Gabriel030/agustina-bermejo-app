package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SubjectEquivalenceVoidedEvent(String correlationId, Instant occurredAt, Long subjectEquivalenceId, Long programEnrollmentId, Long subjectId, Long studentPersonId, String subjectName) implements DomainEvent {

    public static SubjectEquivalenceVoidedEvent voidedFor(Long subjectEquivalenceId, Long programEnrollmentId, Long subjectId, Long studentPersonId, String subjectName) {
        return new SubjectEquivalenceVoidedEvent(CorrelationIds.current(), ApplicationTime.instant(), subjectEquivalenceId, programEnrollmentId, subjectId, studentPersonId, subjectName);
    }
}
