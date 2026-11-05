package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NightlyBillingRunFailedEvent(UUID eventId, String correlationId, Instant occurredAt, Long nightlyBillingRunId, LocalDate businessDate, String failedStep)
        implements DeduplicableDomainEvent {

    public static NightlyBillingRunFailedEvent failedAt(Long nightlyBillingRunId, LocalDate businessDate, String failedStep) {
        UUID oneIdentityPerRun = UUID.nameUUIDFromBytes(("NightlyBillingRunFailed:" + nightlyBillingRunId).getBytes(StandardCharsets.UTF_8));
        return new NightlyBillingRunFailedEvent(oneIdentityPerRun, CorrelationIds.current(), ApplicationTime.instant(), nightlyBillingRunId, businessDate, failedStep);
    }
}
