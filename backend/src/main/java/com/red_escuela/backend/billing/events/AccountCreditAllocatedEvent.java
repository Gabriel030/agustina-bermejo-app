package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountCreditAllocatedEvent(UUID eventId, String correlationId, Instant occurredAt, Long studentCurrentAccountId, List<Long> invoiceIds,
                                          NonNegativeMoney allocatedAmount, LocalDate businessDate) implements DeduplicableDomainEvent {

    public static AccountCreditAllocatedEvent allocatedFor(Long studentCurrentAccountId, List<Long> invoiceIds, NonNegativeMoney allocatedAmount,
                                                           LocalDate businessDate) {
        return new AccountCreditAllocatedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), studentCurrentAccountId,
                List.copyOf(invoiceIds), allocatedAmount, businessDate);
    }
}
