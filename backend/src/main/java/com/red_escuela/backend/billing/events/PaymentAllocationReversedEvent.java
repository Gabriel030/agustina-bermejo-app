package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentAllocationReversedEvent(UUID eventId, String correlationId, Instant occurredAt, Long invoiceId, Long studentCurrentAccountId,
                                             LocalDate businessDate) implements DeduplicableDomainEvent {

    public static PaymentAllocationReversedEvent reversedFor(Long invoiceId, Long studentCurrentAccountId, LocalDate businessDate) {
        return new PaymentAllocationReversedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), invoiceId, studentCurrentAccountId,
                businessDate);
    }
}
