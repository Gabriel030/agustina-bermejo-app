package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record YearBillingConfiguredEvent(String correlationId, Instant occurredAt, Long programId) implements DomainEvent {

    public static YearBillingConfiguredEvent configuredFor(Long programId) {
        return new YearBillingConfiguredEvent(CorrelationIds.current(), ApplicationTime.instant(), programId);
    }
}
