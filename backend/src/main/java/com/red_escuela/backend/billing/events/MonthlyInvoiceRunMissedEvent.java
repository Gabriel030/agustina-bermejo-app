package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MonthlyInvoiceRunMissedEvent(UUID eventId, String correlationId, Instant occurredAt, LocalDate period) implements DeduplicableDomainEvent {

    public static MonthlyInvoiceRunMissedEvent missedFor(LocalDate period) {
        UUID oneIdentityPerPeriod = UUID.nameUUIDFromBytes(("MonthlyInvoiceRunMissed:" + period).getBytes(StandardCharsets.UTF_8));
        return new MonthlyInvoiceRunMissedEvent(oneIdentityPerPeriod, CorrelationIds.current(), ApplicationTime.instant(), period);
    }
}
