package com.red_escuela.backend.architecture.fixture.billing.operation;

import com.red_escuela.backend.architecture.fixture.billing.configuration.entity.FixtureEntity;
import com.red_escuela.backend.shared.event.DomainEvent;

import java.time.Instant;

public record EntityCarryingEventFixture(String correlationId, Instant occurredAt, CarriedEntity entity) implements DomainEvent {

    @FixtureEntity
    public static class CarriedEntity {
    }
}
