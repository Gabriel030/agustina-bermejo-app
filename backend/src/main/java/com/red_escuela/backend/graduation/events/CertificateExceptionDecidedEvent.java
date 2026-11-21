package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CertificateExceptionDecidedEvent(String correlationId, Instant occurredAt, Long exceptionId, Long studentId,
                                               Long requesterPersonId, boolean approved, String decisionReason) implements DomainEvent {

    public static CertificateExceptionDecidedEvent of(Long exceptionId, Long studentId, Long requesterPersonId, boolean approved,
                                                      String decisionReason) {
        return new CertificateExceptionDecidedEvent(CorrelationIds.current(), ApplicationTime.instant(), exceptionId, studentId,
                requesterPersonId, approved, decisionReason);
    }
}
