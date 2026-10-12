package com.red_escuela.backend.billing.invoice.domain.event;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoicePaidDomainEvent(UUID eventId, String correlationId, Instant occurredAt, Long invoiceId, Long studentCurrentAccountId,
                                     String cause, LocalDate businessDate) implements DeduplicableDomainEvent {

    public static InvoicePaidDomainEvent paidFor(Long invoiceId, Long studentCurrentAccountId, String cause, LocalDate businessDate) {
        return new InvoicePaidDomainEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), invoiceId, studentCurrentAccountId,
                cause, businessDate);
    }
}
