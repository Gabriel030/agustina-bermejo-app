package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationCertificateIssuedEvent(String correlationId, Instant occurredAt, Long certificateId, Long studentPersonId, String titleName) implements DomainEvent {

    public static GraduationCertificateIssuedEvent of(Long certificateId, Long studentPersonId, String titleName) {
        return new GraduationCertificateIssuedEvent(CorrelationIds.current(), ApplicationTime.instant(), certificateId, studentPersonId, titleName);
    }
}
