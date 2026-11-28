package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ProgramEnrollmentWithdrawnEvent(String correlationId, Instant occurredAt, Long studentId, Long programEnrollmentId)
        implements DomainEvent {

    public static ProgramEnrollmentWithdrawnEvent withdrawn(Long studentId, Long programEnrollmentId) {
        return new ProgramEnrollmentWithdrawnEvent(CorrelationIds.current(), ApplicationTime.instant(), studentId, programEnrollmentId);
    }
}
