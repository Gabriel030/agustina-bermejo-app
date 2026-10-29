package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NightlyBillingRunCompletedWithErrorsEvent(UUID eventId, String correlationId, Instant occurredAt, Long nightlyBillingRunId, LocalDate businessDate)
        implements DeduplicableDomainEvent {

    public static NightlyBillingRunCompletedWithErrorsEvent completedWithErrors(Long nightlyBillingRunId, LocalDate businessDate) {
        UUID oneIdentityPerRun = UUID.nameUUIDFromBytes(("NightlyBillingRunCompletedWithErrors:" + nightlyBillingRunId).getBytes(StandardCharsets.UTF_8));
        return new NightlyBillingRunCompletedWithErrorsEvent(oneIdentityPerRun, CorrelationIds.current(), ApplicationTime.instant(), nightlyBillingRunId, businessDate);
    }
}
