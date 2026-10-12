package com.red_escuela.backend.shared.event;

import java.time.Instant;

public interface DomainEvent {
    String correlationId();

    Instant occurredAt();
}
