package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CertificateExceptionRequestedEvent(String correlationId, Instant occurredAt, Long exceptionId, Long studentId,
                                                 Long requesterPersonId) implements DomainEvent {

    public static CertificateExceptionRequestedEvent of(Long exceptionId, Long studentId, Long requesterPersonId) {
        return new CertificateExceptionRequestedEvent(CorrelationIds.current(), ApplicationTime.instant(), exceptionId, studentId,
                requesterPersonId);
    }
}
