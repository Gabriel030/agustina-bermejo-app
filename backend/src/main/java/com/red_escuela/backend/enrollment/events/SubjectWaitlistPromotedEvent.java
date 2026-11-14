package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record SubjectWaitlistPromotedEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String subjectName, String sectionName) implements DomainEvent {

    public static SubjectWaitlistPromotedEvent of(Long enrollmentId, Long studentPersonId, String subjectName, String sectionName) {
        return new SubjectWaitlistPromotedEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                subjectName, sectionName);
    }
}
