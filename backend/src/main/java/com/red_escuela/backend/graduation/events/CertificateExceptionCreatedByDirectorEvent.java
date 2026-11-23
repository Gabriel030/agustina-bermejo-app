package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CertificateExceptionCreatedByDirectorEvent(String correlationId, Instant occurredAt, Long exceptionId, Long studentId,
                                                         Long directorPersonId) implements DomainEvent {

    public static CertificateExceptionCreatedByDirectorEvent of(Long exceptionId, Long studentId, Long directorPersonId) {
        return new CertificateExceptionCreatedByDirectorEvent(CorrelationIds.current(), ApplicationTime.instant(), exceptionId, studentId,
                directorPersonId);
    }
}
