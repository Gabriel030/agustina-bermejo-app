package com.red_escuela.backend.architecture.fixture.billing.operation;

import com.red_escuela.backend.shared.event.DomainEvent;

import java.time.Instant;

public record FlatEventFixture(String correlationId, Instant occurredAt, Long personId) implements DomainEvent {
}
