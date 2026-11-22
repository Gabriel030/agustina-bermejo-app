package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationCertificateReissuedEvent(String correlationId, Instant occurredAt, Long certificateId, Long replacedCertificateId,
                                                 Long studentPersonId, String titleName) implements DomainEvent {

    public static GraduationCertificateReissuedEvent of(Long certificateId, Long replacedCertificateId, Long studentPersonId, String titleName) {
        return new GraduationCertificateReissuedEvent(CorrelationIds.current(), ApplicationTime.instant(), certificateId,
                replacedCertificateId, studentPersonId, titleName);
    }
}
