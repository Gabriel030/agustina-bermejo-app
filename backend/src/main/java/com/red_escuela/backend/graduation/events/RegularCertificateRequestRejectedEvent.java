package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record RegularCertificateRequestRejectedEvent(String correlationId, Instant occurredAt, Long requestId, Long studentId,
                                                     Long personId, String reason) implements DomainEvent {

    public static RegularCertificateRequestRejectedEvent of(Long requestId, Long studentId, Long personId, String reason) {
        return new RegularCertificateRequestRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), requestId, studentId,
                personId, reason);
    }
}
