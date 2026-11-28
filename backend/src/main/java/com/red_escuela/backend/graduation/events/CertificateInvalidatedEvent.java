package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CertificateInvalidatedEvent(String correlationId, Instant occurredAt, Long certificateId, String certificateKind,
                                          Long personId, String reason) implements DomainEvent {

    public static final String REGULAR = "REGULAR";
    public static final String APPROVED_SUBJECTS = "APPROVED_SUBJECTS";

    public static CertificateInvalidatedEvent of(Long certificateId, String certificateKind, Long personId, String reason) {
        return new CertificateInvalidatedEvent(CorrelationIds.current(), ApplicationTime.instant(), certificateId, certificateKind,
                personId, reason);
    }
}
