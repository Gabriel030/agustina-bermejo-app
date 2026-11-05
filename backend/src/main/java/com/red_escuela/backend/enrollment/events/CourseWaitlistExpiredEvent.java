package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CourseWaitlistExpiredEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId, String courseName)
        implements DomainEvent {

    public static CourseWaitlistExpiredEvent expiredFor(Long enrollmentId, Long studentPersonId, String courseName) {
        return new CourseWaitlistExpiredEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId, courseName);
    }
}
