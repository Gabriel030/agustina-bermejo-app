package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SubjectEquivalenceRecordedEvent(String correlationId, Instant occurredAt, Long subjectEquivalenceId, Long programEnrollmentId, Long subjectId, Long studentPersonId, String subjectName) implements DomainEvent {

    public static SubjectEquivalenceRecordedEvent recordedFor(Long subjectEquivalenceId, Long programEnrollmentId, Long subjectId, Long studentPersonId, String subjectName) {
        return new SubjectEquivalenceRecordedEvent(CorrelationIds.current(), ApplicationTime.instant(), subjectEquivalenceId, programEnrollmentId, subjectId, studentPersonId, subjectName);
    }
}
