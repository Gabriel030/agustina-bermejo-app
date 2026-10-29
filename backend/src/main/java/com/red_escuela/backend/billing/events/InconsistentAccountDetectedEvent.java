package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InconsistentAccountDetectedEvent(UUID eventId, String correlationId, Instant occurredAt, Long accountId, LocalDate businessDate, String findingCode,
                                               String detail) implements DeduplicableDomainEvent {

    public static InconsistentAccountDetectedEvent detectedAt(Long accountId, LocalDate businessDate, String findingCode, String detail) {
        return new InconsistentAccountDetectedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), accountId, businessDate, findingCode, detail);
    }
}
