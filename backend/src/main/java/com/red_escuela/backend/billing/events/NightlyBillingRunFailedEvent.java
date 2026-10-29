package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NightlyBillingRunFailedEvent(UUID eventId, String correlationId, Instant occurredAt, Long nightlyBillingRunId, LocalDate businessDate, String failedStep,
                                           String failureMessage) implements DeduplicableDomainEvent {

    public static NightlyBillingRunFailedEvent failedAt(Long nightlyBillingRunId, LocalDate businessDate, String failedStep, String failureMessage) {
        return new NightlyBillingRunFailedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), nightlyBillingRunId, businessDate, failedStep,
                failureMessage);
    }
}
