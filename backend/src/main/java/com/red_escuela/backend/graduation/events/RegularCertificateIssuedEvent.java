package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record RegularCertificateIssuedEvent(String correlationId, Instant occurredAt, Long certificateId, Long studentId) implements DomainEvent {

    public static RegularCertificateIssuedEvent issued(Long certificateId, Long studentId) {
        return new RegularCertificateIssuedEvent(CorrelationIds.current(), ApplicationTime.instant(), certificateId, studentId);
    }
}
