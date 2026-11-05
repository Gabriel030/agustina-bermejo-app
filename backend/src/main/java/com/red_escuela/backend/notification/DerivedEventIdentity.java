package com.red_escuela.backend.notification;

import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.DomainEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

record DerivedEventIdentity(UUID eventId, String correlationId, Instant occurredAt) implements DeduplicableDomainEvent {

    static DerivedEventIdentity of(DomainEvent event) {
        String fingerprint = event.getClass().getName() + "|" + event;
        return new DerivedEventIdentity(UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)), event.correlationId(), event.occurredAt());
    }
}
