package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record RegularCertificateRequestFulfilledEvent(String correlationId, Instant occurredAt, Long certificateId, Long studentId,
                                                      Long personId) implements DomainEvent {

    public static RegularCertificateRequestFulfilledEvent of(Long certificateId, Long studentId, Long personId) {
        return new RegularCertificateRequestFulfilledEvent(CorrelationIds.current(), ApplicationTime.instant(), certificateId,
                studentId, personId);
    }
}
