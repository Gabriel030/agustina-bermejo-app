package com.red_escuela.backend.shared.event;

import java.util.UUID;

public interface DeduplicableDomainEvent extends DomainEvent {
    UUID eventId();
}
