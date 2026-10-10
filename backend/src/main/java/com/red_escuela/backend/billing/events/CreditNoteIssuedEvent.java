package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreditNoteIssuedEvent(UUID eventId, String correlationId, Instant occurredAt, Long creditNoteId, String creditNoteNumber, Long invoiceId,
                                    String invoiceNumber, Long studentPersonId, Long requestedByPersonId, String reason, BigDecimal totalAmount,
                                    String currency, LocalDate issueDate) implements DeduplicableDomainEvent {

    public static CreditNoteIssuedEvent issuedFor(Long creditNoteId, String creditNoteNumber, Long invoiceId, String invoiceNumber, Long studentPersonId,
                                                  Long requestedByPersonId, String reason, BigDecimal totalAmount, String currency, LocalDate issueDate) {
        return new CreditNoteIssuedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), creditNoteId, creditNoteNumber, invoiceId,
                invoiceNumber, studentPersonId, requestedByPersonId, reason, totalAmount, currency, issueDate);
    }
}
