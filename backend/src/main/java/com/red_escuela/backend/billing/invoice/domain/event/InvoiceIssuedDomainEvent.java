package com.red_escuela.backend.billing.invoice.domain.event;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceIssuedDomainEvent(UUID eventId, String correlationId, Instant occurredAt, Long invoiceId, Long studentCurrentAccountId,
                                       String invoiceNumber, String invoiceType, NonNegativeMoney total, LocalDate issueDate, LocalDate dueDate)
        implements DeduplicableDomainEvent {

    public static InvoiceIssuedDomainEvent issuedFor(Long invoiceId, Long studentCurrentAccountId, String invoiceNumber, String invoiceType,
                                                     NonNegativeMoney total, LocalDate issueDate, LocalDate dueDate) {
        return new InvoiceIssuedDomainEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), invoiceId, studentCurrentAccountId,
                invoiceNumber, invoiceType, total, issueDate, dueDate);
    }
}
